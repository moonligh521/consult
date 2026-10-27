package com.moonligh.consult.identity.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.consult.identity.domain.OccupancyService;
import com.moonligh.consult.identity.domain.OccupancyStore;
import com.moonligh.consult.identity.domain.SlotFreedSink;
import com.moonligh.consult.identity.store.DoctorStore;

/** 占坑服务。Clock 方便单测拨时间。 */
@Configuration
public class OccupancyConfiguration {

    @Bean
    public Clock occupancyClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OccupancyService occupancyService(
            OccupancyStore store,
            DoctorStore doctors,
            Clock occupancyClock,
            ObjectProvider<SlotFreedSink> slotFreed) {
        return new OccupancyService(store, doctors::releaseFlight, occupancyClock, doctors, slotFreed.getIfAvailable());
    }
}
