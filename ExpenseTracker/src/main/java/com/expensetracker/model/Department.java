package com.expensetracker.model;

/**
 * Модель, представляющая отдел (Department) фирмы.
 */
public class Department {
    private int id;
    private String name;

    public Department() {
    }

    public Department(int id, String name) {
        this.id = id;
        this.name = name;
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

    // Удобный вывод в интерфейсе (например, в ComboBox)
    @Override
    public String toString() {
        return getName();
    }
}
