package com.moro;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;


public class ClassicalMusic implements Music {

    @PostConstruct
    public void doMyInitialization() {
        System.out.println("Doing my initialization..");
    }
    @PreDestroy
    public void doMyDestruction() {
        System.out.println("Doing my destruction..");
    }

    @Override
    public String getSong() {
        return "Hungarian Rhapsody";
    }

}
