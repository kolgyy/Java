package com.moro;

import org.springframework.stereotype.Component;

import java.util.List;


public class RockMusic implements Music {
    @Override
    public String getSong() {
        return "Wind cries Mary";
    }
}
