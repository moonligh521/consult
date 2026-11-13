package com.moonligh.consult.agent.infrastructure.judge;

import java.util.List;

import com.moonligh.consult.agent.domain.judge.TurnJudge;

public interface LangfuseTurnPort {

    List<TurnJudge.Observation> fetchTurns(int sinceMinutes, int limit);

    void postScore(TurnJudge.Score score);
}
