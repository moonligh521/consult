package com.moonligh.benefit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 账号上的权益卡。次数只抵同一档；没有次数再用抵扣金。快到期的卡先用。
 * 同一订单号重复预占得到同一次结果。释放之后同一订单号可以再占。
 */
public final class BenefitBook {

    static final int HOLD = 1;
    static final int CONFIRM = 2;
    static final int RELEASE = 3;
    private static final Set<String> ITEMS = Set.of("P1", "P2", "P3", "P4", "CREDIT");

    private final BenefitStore store;

    public BenefitBook(BenefitStore store) {
        this.store = store;
    }

    public void define(Product product) {
        guard(() -> {
            if (product == null || blank(product.code()) || blank(product.name()) || product.validDays() < 1) {
                throw new BenefitException("PARAM");
            }
            if (product.items() == null || product.items().isEmpty()) {
                throw new BenefitException("PARAM");
            }
            List<ProductItem> items = new ArrayList<>();
            for (ProductItem item : product.items()) {
                if (item == null || !ITEMS.contains(item.itemCode()) || item.quantity() == null) {
                    throw new BenefitException("PARAM");
                }
                BigDecimal quantity = money(item.quantity());
                if (quantity.signum() <= 0) {
                    throw new BenefitException("PARAM");
                }
                if (!"CREDIT".equals(item.itemCode()) && quantity.stripTrailingZeros().scale() > 0) {
                    throw new BenefitException("PARAM");
                }
                items.add(new ProductItem(item.itemCode(), quantity));
            }
            store.saveProduct(new Product(product.code().trim(), product.name().trim(), product.validDays(), List.copyOf(items)));
            return null;
        });
    }

    public Issued issue(Issue issue, Instant now) {
        return guard(() -> {
            if (issue == null || blank(issue.accountId()) || blank(issue.productCode())
                    || blank(issue.channel()) || blank(issue.sourceRef()) || now == null) {
                throw new BenefitException("PARAM");
            }
            String channel = issue.channel().trim();
            String ref = issue.sourceRef().trim();
            Issued existing = store.findBySource(channel, ref);
            if (existing != null) {
                if (!existing.accountId().equals(issue.accountId().trim())) {
                    throw new BenefitException("CONFLICT");
                }
                return existing;
            }
            Product product = store.findProduct(issue.productCode().trim());
            if (product == null) {
                throw new BenefitException("NOT_FOUND");
            }
            String cardId = UUID.randomUUID().toString();
            Instant until = now.plus(product.validDays(), ChronoUnit.DAYS);
            List<Balance> balances = new ArrayList<>();
            for (ProductItem item : product.items()) {
                balances.add(new Balance(item.itemCode(), item.quantity()));
            }
            store.insertCard(new NewCard(
                    cardId,
                    issue.accountId().trim(),
                    product.code(),
                    channel,
                    ref,
                    now,
                    until,
                    balances));
            return new Issued(cardId, issue.accountId().trim(), product.code());
        });
    }

    public Quote reserve(Reserve reserve, Instant now) {
        return guard(() -> {
            if (reserve == null || blank(reserve.accountId()) || blank(reserve.orderId()) || now == null
                    || reserve.packageCode() < 1 || reserve.packageCode() > 4 || reserve.catalogAmount() == null) {
                throw new BenefitException("PARAM");
            }
            BigDecimal catalog = money(reserve.catalogAmount());
            if (catalog.signum() <= 0) {
                throw new BenefitException("PARAM");
            }
            String orderId = reserve.orderId().trim();
            Hold held = store.findHold(orderId);
            if (held != null && held.status() != RELEASE) {
                if (!held.accountId().equals(reserve.accountId().trim())) {
                    throw new BenefitException("CONFLICT");
                }
                return quoteOf(held);
            }
            String countItem = "P" + reserve.packageCode();
            List<Bucket> buckets = store.lockAccount(reserve.accountId().trim(), now);
            buckets.sort(Comparator.comparing(Bucket::validTo).thenComparing(Bucket::cardId));
            Bucket chosen = null;
            BigDecimal take = null;
            int holdCount = 0;
            for (Bucket bucket : buckets) {
                if (countItem.equals(bucket.itemCode()) && bucket.remaining().compareTo(BigDecimal.ONE) >= 0) {
                    chosen = bucket;
                    take = money(BigDecimal.ONE);
                    holdCount = 1;
                    break;
                }
            }
            if (chosen == null) {
                for (Bucket bucket : buckets) {
                    if ("CREDIT".equals(bucket.itemCode()) && bucket.remaining().signum() > 0) {
                        chosen = bucket;
                        take = bucket.remaining().min(catalog);
                        take = money(take);
                        break;
                    }
                }
            }
            if (chosen == null || take == null || take.signum() <= 0) {
                if (held != null) {
                    store.deleteHold(orderId);
                }
                return new Quote(orderId, null, null, money(BigDecimal.ZERO), catalog);
            }
            if (!store.take(chosen.cardId(), chosen.itemCode(), take)) {
                throw new BenefitException("CONFLICT");
            }
            BigDecimal cash = money(catalog.subtract(holdCount == 1 ? catalog : take));
            BigDecimal covered = money(catalog.subtract(cash));
            Hold next = new Hold(
                    orderId,
                    reserve.accountId().trim(),
                    chosen.cardId(),
                    chosen.itemCode(),
                    holdCount,
                    holdCount == 1 ? money(BigDecimal.ZERO) : take,
                    cash,
                    catalog,
                    HOLD);
            if (held == null) {
                store.insertHold(next);
            } else {
                store.replaceHold(next);
            }
            return quoteOf(next);
        });
    }

    public void confirm(String orderId) {
        guard(() -> {
            move(orderId, HOLD, CONFIRM, false);
            return null;
        });
    }

    public void release(String orderId) {
        guard(() -> {
            move(orderId, HOLD, RELEASE, true);
            return null;
        });
    }

    public void restore(String orderId) {
        guard(() -> {
            Hold held = store.findHold(orderId);
            if (held == null || held.status() == RELEASE) {
                return null;
            }
            give(held);
            store.updateStatus(orderId, RELEASE);
            return null;
        });
    }

    public List<BenefitLine> account(String accountId, Instant now) {
        return guard(() -> {
            if (blank(accountId) || now == null) {
                throw new BenefitException("PARAM");
            }
            return store.lines(accountId.trim(), now);
        });
    }

    /** 内存账整段串行。数据库账本靠事务和行锁，不同账号同时预占。 */
    private <T> T guard(Supplier<T> work) {
        if (store.serializesCaller()) {
            synchronized (store) {
                return work.get();
            }
        }
        return work.get();
    }

    private void move(String orderId, int from, int to, boolean giveBack) {
        Hold held = store.findHold(orderId);
        if (held == null || held.status() != from) {
            return;
        }
        if (giveBack) {
            give(held);
        }
        store.updateStatus(orderId, to);
    }

    private void give(Hold held) {
        BigDecimal amount = held.holdCount() > 0 ? money(BigDecimal.ONE) : held.holdAmount();
        store.giveBack(held.cardId(), held.itemCode(), amount);
    }

    private static Quote quoteOf(Hold held) {
        return new Quote(
                held.orderId(),
                held.cardId(),
                held.itemCode(),
                money(held.catalogAmount().subtract(held.cashDue())),
                held.cashDue());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
