package com.titanium.maintenance.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.modelling.command.Repository;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.maintenance.aggregate.Maintenance;

@Configuration
public class AxonConfig {
    @Bean
    public CommandBus commandBus() {
        return SimpleCommandBus.builder().build();
    }

    @Bean
    public CommandGateway commandGateway(CommandBus commandBus) {
        return org.axonframework.commandhandling.gateway.DefaultCommandGateway.builder()
                .commandBus(commandBus)
                .build();
    }

    @Bean
    public Repository<Maintenance> maintenanceRepository(EventStore eventStore, AxonConfiguration axonConfiguration) {
        return EventSourcingRepository.builder(Maintenance.class)
                .eventStore(eventStore)
                .build();
    }
}