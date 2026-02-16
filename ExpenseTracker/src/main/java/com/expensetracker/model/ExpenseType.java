package com.expensetracker.model;

import java.math.BigDecimal;

/**
 * Модель, представляющая вид расходов.
 */
public class ExpenseType {
    private int id;
    private String name;
    private String description;
    private BigDecimal monthlyLimit;

    public ExpenseType() {
    }

    public ExpenseType(int id, String name, String description, BigDecimal monthlyLimit) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.monthlyLimit = monthlyLimit;
    }

    // Геттеры и сеттеры
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    @Override
    public String toString() {
        return name;
    }
}
