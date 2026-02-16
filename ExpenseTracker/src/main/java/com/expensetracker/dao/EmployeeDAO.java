package com.expensetracker.dao;

import com.expensetracker.db.DatabaseConnection;
import com.expensetracker.model.Department;
import com.expensetracker.model.Employee;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDAO {

    public List<Employee> getAll() throws SQLException {
        List<Employee> list = new ArrayList<>();

        String sql = "SELECT e.id, e.full_name, d.id AS department_id, d.name AS department_name " +
                "FROM Employees e " +
                "JOIN Departments d ON e.department_id = d.id";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Department department = new Department(
                        rs.getInt("department_id"),
                        rs.getString("department_name")
                );

                Employee employee = new Employee(
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        department
                );

                list.add(employee);
            }
        }
        return list;
    }

    public Employee getById(int id) throws SQLException {
        String sql = "SELECT e.id, e.full_name, d.id AS department_id, d.name AS department_name " +
                "FROM Employees e " +
                "JOIN Departments d ON e.department_id = d.id " +
                "WHERE e.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Department department = new Department(
                            rs.getInt("department_id"),
                            rs.getString("department_name")
                    );

                    return new Employee(
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            department
                    );
                }
            }
        }
        return null;
    }

    public int insert(Employee emp) throws SQLException {
        String sql = "INSERT INTO Employees (full_name, department_id) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, emp.getFullName());
            stmt.setInt(2, emp.getDepartment().getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Создание сотрудника не удалось, нет затронутых строк.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    emp.setId(newId);
                    return newId;
                } else {
                    throw new SQLException("Создание сотрудника не удалось, не удалось получить ID.");
                }
            }
        }
    }

    public void update(Employee emp) throws SQLException {
        String sql = "UPDATE Employees SET full_name = ?, department_id = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, emp.getFullName());
            stmt.setInt(2, emp.getDepartment().getId());
            stmt.setInt(3, emp.getId());

            stmt.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM Employees WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
        catch (SQLIntegrityConstraintViolationException e) {
            // Специально пробрасываем дальше, чтобы контроллер отобразил красивое сообщение
            throw e;
        } catch (SQLException e) {
            // Все прочие SQL-ошибки
            throw new SQLException("Ошибка при удалении отдела с id=" + id + ": " + e.getMessage(), e);
        }
    }
}
