package com.expensetracker.dao;

import com.expensetracker.db.DatabaseConnection;
import com.expensetracker.model.ExpenseType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExpenseTypeDAO {

    public List<ExpenseType> getAll() throws SQLException {
        List<ExpenseType> list = new ArrayList<>();
        String sql = "SELECT id, name, description, monthly_limit FROM ExpenseTypes";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new ExpenseType(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("monthly_limit")
                ));
            }
        }
        return list;
    }

    public ExpenseType getById(int id) throws SQLException {
        String sql = "SELECT id, name, description, monthly_limit FROM ExpenseTypes WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ExpenseType(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBigDecimal("monthly_limit")
                    );
                }
            }
        }
        return null;
    }

    public int insert(ExpenseType expense) throws SQLException {
        String sql = "INSERT INTO ExpenseTypes (name, description, monthly_limit) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, expense.getName());
            stmt.setString(2, expense.getDescription());
            stmt.setBigDecimal(3, expense.getMonthlyLimit());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Создание вида расхода не удалось, нет затронутых строк.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    expense.setId(newId);
                    return newId;
                } else {
                    throw new SQLException("Создание вида расхода не удалось, не удалось получить ID.");
                }
            }
        }
    }

    public void update(ExpenseType expense) throws SQLException {
        String sql = "UPDATE ExpenseTypes SET name = ?, description = ?, monthly_limit = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, expense.getName());
            stmt.setString(2, expense.getDescription());
            stmt.setBigDecimal(3, expense.getMonthlyLimit());
            stmt.setInt(4, expense.getId());

            stmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM ExpenseTypes WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }
}
