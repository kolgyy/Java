package com.expensetracker.model;

/**
 * Модель, представляющая сотрудника фирмы.
 */
public class Employee {
    private int id;
    private String fullName;
    private Department department;

    public Employee() {
    }

    public Employee(int id, String fullName, Department department) {
        this.id = id;
        this.fullName = fullName;
        this.department = department;
    }

    // Геттеры и сеттеры
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
