package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Модель, представляющая покупку (расход).
 */
public class Purchase {
    private int id;
    private int employeeId;
    private int expenseTypeId;
    private LocalDate purchaseDate;
    private BigDecimal amount;
    private String receiptNumber;

    // Дополнительные ссылки на связанные объекты
    private Employee employee;
    private ExpenseType expenseType;

    // Конструкторы
    public Purchase() {}

    public Purchase(int id, int employeeId, int expenseTypeId, LocalDate purchaseDate,
                    BigDecimal amount, String receiptNumber) {
        this.id = id;
        this.employeeId = employeeId;
        this.expenseTypeId = expenseTypeId;
        this.purchaseDate = purchaseDate;
        this.amount = amount;
        this.receiptNumber = receiptNumber;
    }

    // Геттеры и сеттеры

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public int getExpenseTypeId() {
        return expenseTypeId;
    }

    public void setExpenseTypeId(int expenseTypeId) {
        this.expenseTypeId = expenseTypeId;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    // Связанные сущности

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public void setExpenseType(ExpenseType expenseType) {
        this.expenseType = expenseType;
    }
}
