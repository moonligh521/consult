package com.moonligh.consult.identity.infrastructure.load;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.moonligh.consult.identity.domain.load.DoctorClaim;
import com.moonligh.consult.identity.domain.load.DoctorLoadPool;
import com.moonligh.consult.identity.domain.load.DoctorSeat;

/**
 * Redis 三层 ZSET。领取、放回、释放各用一条 Lua，避免两个实例领到同一个人。
 * 键按二级科和档位分开。MySQL 占坑失败或 Redis 不可用时，调用方改走数据库选人。
 */
public final class RedisDoctorLoadPool implements DoctorLoadPool {

    private static final Logger log = LoggerFactory.getLogger(RedisDoctorLoadPool.class);

    private static final DefaultRedisScript<String> CLAIM = script(String.class, """
            local existing = redis.call('GET', KEYS[1])
            if existing and existing ~= '' then
              return existing
            end
            local function laneOf(n)
              if n == 0 then return KEYS[3] end
              if n == 1 then return KEYS[4] end
              return KEYS[5]
            end
            local function where(id)
              if redis.call('ZSCORE', KEYS[3], id) then return 0 end
              if redis.call('ZSCORE', KEYS[4], id) then return 1 end
              if redis.call('ZSCORE', KEYS[5], id) then return 2 end
              return -1
            end
            local now = tonumber(ARGV[1])
            local allowed = {}
            local i = 2
            while i + 3 <= #ARGV do
              local id = ARGV[i]
              local inflight = tonumber(ARGV[i + 1]) or 0
              local idle = tonumber(ARGV[i + 2]) or 0
              local cap = tonumber(ARGV[i + 3]) or 3
              if cap < 1 then cap = 1 end
              redis.call('HSET', KEYS[7], id, cap)
              if redis.call('HEXISTS', KEYS[6], id) == 0 then
                allowed[#allowed + 1] = id
                if where(id) < 0 then
                  local load = redis.call('HGET', KEYS[2], id)
                  if not load then
                    load = inflight
                    redis.call('HSET', KEYS[2], id, load)
                  else
                    load = tonumber(load)
                  end
                  if load < cap then
                    local bucket = load
                    if bucket < 0 then bucket = 0 end
                    if bucket > 2 then bucket = 2 end
                    local score = idle
                    if score <= 0 then score = now end
                    redis.call('ZADD', laneOf(bucket), score, id)
                  end
                end
              end
              i = i + 4
            end
            local function pick(lane)
              local rows = redis.call('ZRANGE', lane, 0, -1)
              for j = 1, #rows do
                local id = rows[j]
                for k = 1, #allowed do
                  if allowed[k] == id then
                    return id, redis.call('ZSCORE', lane, id)
                  end
                end
              end
              return nil, nil
            end
            local chosen, chosenScore, fromLane
            for lane = 0, 2 do
              local id, score = pick(laneOf(lane))
              if id then
                chosen = id
                chosenScore = score
                fromLane = lane
                break
              end
            end
            if not chosen then
              return ''
            end
            redis.call('ZREM', laneOf(fromLane), chosen)
            local load = redis.call('HINCRBY', KEYS[2], chosen, 1)
            local cap = tonumber(redis.call('HGET', KEYS[7], chosen)) or 3
            if load < cap then
              local bucket = load
              if bucket > 2 then bucket = 2 end
              redis.call('ZADD', laneOf(bucket), now, chosen)
            elseif load > cap then
              redis.call('HSET', KEYS[2], chosen, cap)
              redis.call('ZADD', laneOf(fromLane), chosenScore, chosen)
              return ''
            end
            local packed = chosen .. '|' .. fromLane .. '|' .. chosenScore
            redis.call('SET', KEYS[1], packed, 'EX', 86400)
            return packed
            """);

    private static final DefaultRedisScript<Long> RESTORE = script(Long.class, """
            local raw = redis.call('GET', KEYS[1])
            if not raw or raw == '' then
              return 0
            end
            local id = string.match(raw, '^([^|]+)|')
            if id ~= ARGV[1] then
              return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[3], id)
            redis.call('ZREM', KEYS[4], id)
            redis.call('ZREM', KEYS[5], id)
            local load = redis.call('HINCRBY', KEYS[2], id, -1)
            if load < 0 then
              redis.call('HSET', KEYS[2], id, 0)
            end
            local lane = tonumber(ARGV[2]) or 0
            local score = ARGV[3]
            local key = KEYS[3]
            if lane == 1 then key = KEYS[4]
            elseif lane == 2 then key = KEYS[5] end
            redis.call('ZADD', key, score, id)
            return 1
            """);

    private static final DefaultRedisScript<Long> RELEASE = script(Long.class, """
            local id = ARGV[1]
            local now = tonumber(ARGV[2])
            redis.call('ZREM', KEYS[3], id)
            redis.call('ZREM', KEYS[4], id)
            redis.call('ZREM', KEYS[5], id)
            local load = redis.call('HINCRBY', KEYS[2], id, -1)
            if load < 0 then
              load = 0
              redis.call('HSET', KEYS[2], id, 0)
            end
            local cap = tonumber(redis.call('HGET', KEYS[7], id)) or 3
            if redis.call('HEXISTS', KEYS[6], id) == 0 and load < cap then
              local bucket = load
              if bucket < 0 then bucket = 0 end
              if bucket > 2 then bucket = 2 end
              local key = KEYS[3]
              if bucket == 1 then key = KEYS[4]
              elseif bucket == 2 then key = KEYS[5] end
              redis.call('ZADD', key, now, id)
            end
            return load
            """);

    private static final DefaultRedisScript<Long> ONLINE = script(Long.class, """
            local id = ARGV[1]
            local now = tonumber(ARGV[2])
            local load = tonumber(ARGV[3]) or 0
            local cap = tonumber(ARGV[4]) or 3
            if load < 0 then load = 0 end
            if cap < 1 then cap = 1 end
            redis.call('HDEL', KEYS[6], id)
            redis.call('ZREM', KEYS[3], id)
            redis.call('ZREM', KEYS[4], id)
            redis.call('ZREM', KEYS[5], id)
            redis.call('HSET', KEYS[2], id, load)
            redis.call('HSET', KEYS[7], id, cap)
            if load < cap then
              local bucket = load
              if bucket < 0 then bucket = 0 end
              if bucket > 2 then bucket = 2 end
              local key = KEYS[3]
              if bucket == 1 then key = KEYS[4]
              elseif bucket == 2 then key = KEYS[5] end
              redis.call('ZADD', key, now, id)
            end
            return load
            """);

    private static final DefaultRedisScript<Long> OFFLINE = script(Long.class, """
            redis.call('HSET', KEYS[6], ARGV[1], '1')
            redis.call('ZREM', KEYS[3], ARGV[1])
            redis.call('ZREM', KEYS[4], ARGV[1])
            redis.call('ZREM', KEYS[5], ARGV[1])
            return 1
            """);

    private final StringRedisTemplate redis;

    public RedisDoctorLoadPool(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public DoctorClaim claim(String orderId, String specialty, int grade, List<DoctorSeat> allowed, long nowMillis) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        List<String> args = new ArrayList<>();
        args.add(Long.toString(nowMillis));
        if (allowed != null) {
            for (DoctorSeat seat : allowed) {
                if (seat == null || seat.doctorId() == null || seat.doctorId().isBlank()) {
                    continue;
                }
                args.add(seat.doctorId());
                args.add(Integer.toString(Math.max(0, seat.inFlight())));
                args.add(Long.toString(Math.max(0, seat.idleSinceMillis())));
                args.add(Integer.toString(Math.max(1, seat.concurrentLimit())));
            }
        }
        String packed = redis.execute(CLAIM, keys(orderId, specialty, grade), args.toArray());
        return parse(packed);
    }

    @Override
    public void restore(String orderId, DoctorClaim claim, String specialty, int grade) {
        if (orderId == null || orderId.isBlank() || claim == null) {
            return;
        }
        redis.execute(
                RESTORE,
                keys(orderId, specialty, grade),
                claim.doctorId(),
                Integer.toString(claim.lane()),
                Long.toString(claim.score()));
    }

    @Override
    public void release(String doctorId, String specialty, int grade, long nowMillis) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        redis.execute(RELEASE, keys("", specialty, grade), doctorId, Long.toString(nowMillis));
    }

    @Override
    public void online(String doctorId, String specialty, int grade, int inFlight, long nowMillis) {
        online(doctorId, specialty, grade, inFlight, 3, nowMillis);
    }

    @Override
    public void online(
            String doctorId, String specialty, int grade, int inFlight, int concurrentLimit, long nowMillis) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        redis.execute(
                ONLINE,
                keys("", specialty, grade),
                doctorId,
                Long.toString(nowMillis),
                Integer.toString(Math.max(0, inFlight)),
                Integer.toString(Math.max(1, concurrentLimit)));
    }

    @Override
    public void offline(String doctorId, String specialty, int grade) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        redis.execute(OFFLINE, keys("", specialty, grade), doctorId);
    }

    static List<String> keys(String orderId, String specialty, int grade) {
        String lane = "consult:load:" + sanitize(specialty) + ":" + grade;
        String claim = orderId == null || orderId.isBlank() ? lane + ":claim:none" : "consult:load:claim:" + orderId;
        return List.of(claim, lane + ":n", lane + ":0", lane + ":1", lane + ":2", "consult:load:off", "consult:load:cap");
    }

    static DoctorClaim parse(String packed) {
        if (packed == null || packed.isBlank()) {
            return null;
        }
        int first = packed.indexOf('|');
        int second = packed.indexOf('|', first + 1);
        if (first <= 0 || second <= first) {
            log.warn("公平队列返回无法解析");
            return null;
        }
        try {
            return new DoctorClaim(
                    packed.substring(0, first),
                    Integer.parseInt(packed.substring(first + 1, second)),
                    Long.parseLong(packed.substring(second + 1)));
        } catch (NumberFormatException error) {
            log.warn("公平队列返回无法解析");
            return null;
        }
    }

    private static String sanitize(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return "_";
        }
        return specialty.replace(':', '_').replace(' ', '_');
    }

    private static <T> DefaultRedisScript<T> script(Class<T> type, String text) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setResultType(type);
        script.setScriptText(text);
        return script;
    }
}
