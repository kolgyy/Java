package com.expensetracker.dao;

import com.expensetracker.db.DatabaseConnection;
import com.expensetracker.model.Department;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DepartmentDAO {

    public List<Department> getAll() throws SQLException {
        List<Department> list = new ArrayList<>();
        String sql = "SELECT id, name FROM Departments";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Department(rs.getInt("id"), rs.getString("name")));
            }
        }
        return list;
    }

    public Department getById(int id) throws SQLException {
        String sql = "SELECT id, name FROM Departments WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Department(rs.getInt("id"), rs.getString("name"));
                }
            }
        }
        return null;
    }

    public int insert(Department d) throws SQLException {
        String sql = "INSERT INTO Departments (name) VALUES (?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, d.getName());
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Создание отдела не удалось, нет затронутых строк.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    d.setId(newId);
                    return newId;
                } else {
                    throw new SQLException("Создание отдела не удалось, не удалось получить ID.");
                }
            }
        }
    }

    public void update(Department department) throws SQLException {
        String sql = "UPDATE Departments SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, department.getName());
            stmt.setInt(2, department.getId());
            stmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM Departments WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            // Специально пробрасываем дальше, чтобы контроллер отобразил красивое сообщение
            throw e;
        } catch (SQLException e) {
            // Все прочие SQL-ошибки
            throw new SQLException("Ошибка при удалении отдела с id=" + id + ": " + e.getMessage(), e);
        }
    }
}
