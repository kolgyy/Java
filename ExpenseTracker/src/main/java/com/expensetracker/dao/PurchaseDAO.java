package com.expensetracker.dao;

import com.expensetracker.model.Purchase;
import com.expensetracker.model.Employee;
import com.expensetracker.model.ExpenseType;
import com.expensetracker.db.DatabaseConnection;

import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PurchaseDAO {

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ExpenseTypeDAO expenseTypeDAO = new ExpenseTypeDAO();

    public List<Purchase> getAll() throws SQLException {
        List<Purchase> list = new ArrayList<>();
        String sql = "SELECT id, employee_id, expense_type_id, purchase_date, amount, receipt_number FROM Purchases";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Purchase purchase = new Purchase(
                        rs.getInt("id"),
                        rs.getInt("employee_id"),
                        rs.getInt("expense_type_id"),
                        rs.getDate("purchase_date").toLocalDate(),
                        rs.getBigDecimal("amount"),
                        rs.getString("receipt_number")
                );

                // Устанавливаем связанные сущности
                purchase.setEmployee(employeeDAO.getById(purchase.getEmployeeId()));
                purchase.setExpenseType(expenseTypeDAO.getById(purchase.getExpenseTypeId()));

                list.add(purchase);
            }
        }

        return list;
    }

    public Purchase getById(int id) throws SQLException {
        String sql = "SELECT * FROM Purchases WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Purchase purchase = new Purchase(
                            rs.getInt("id"),
                            rs.getInt("employee_id"),
                            rs.getInt("expense_type_id"),
                            rs.getDate("purchase_date").toLocalDate(),
                            rs.getBigDecimal("amount"),
                            rs.getString("receipt_number")
                    );

                    purchase.setEmployee(employeeDAO.getById(purchase.getEmployeeId()));
                    purchase.setExpenseType(expenseTypeDAO.getById(purchase.getExpenseTypeId()));

                    return purchase;
                }
            }
        }

        return null;
    }

    public void insert(Purchase purchase) throws SQLException {
        String sql = "INSERT INTO Purchases (employee_id, expense_type_id, purchase_date, amount, receipt_number) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, purchase.getEmployeeId());
            stmt.setInt(2, purchase.getExpenseTypeId());
            stmt.setDate(3, Date.valueOf(purchase.getPurchaseDate()));
            stmt.setBigDecimal(4, purchase.getAmount());
            stmt.setString(5, purchase.getReceiptNumber());

            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    purchase.setId(generatedKeys.getInt(1));
                }
            }
        }
    }

    public void update(Purchase purchase) throws SQLException {
        String sql = "UPDATE Purchases SET employee_id = ?, expense_type_id = ?, purchase_date = ?, amount = ?, receipt_number = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, purchase.getEmployeeId());
            stmt.setInt(2, purchase.getExpenseTypeId());
            stmt.setDate(3, Date.valueOf(purchase.getPurchaseDate()));
            stmt.setBigDecimal(4, purchase.getAmount());
            stmt.setString(5, purchase.getReceiptNumber());
            stmt.setInt(6, purchase.getId());

            stmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM Purchases WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Получает сумму расходов по конкретному типу расхода за указанный год и месяц.
     * Исключает покупку с указанным excludePurchaseId (например, при редактировании).
     * Если excludePurchaseId == 0, то не исключает никакую покупку.
     */
    public BigDecimal getSumAmountByExpenseTypeAndMonth(int expenseTypeId, int year, int month, int excludePurchaseId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) AS total " +
                "FROM Purchases " +
                "WHERE expense_type_id = ? " +
                "AND YEAR(purchase_date) = ? " +
                "AND MONTH(purchase_date) = ? ";

        if (excludePurchaseId > 0) {
            sql += "AND id <> ? ";
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, expenseTypeId);
            stmt.setInt(2, year);
            stmt.setInt(3, month);

            if (excludePurchaseId > 0) {
                stmt.setInt(4, excludePurchaseId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total");
                }
            }
        }
        return BigDecimal.ZERO;
    }
}
