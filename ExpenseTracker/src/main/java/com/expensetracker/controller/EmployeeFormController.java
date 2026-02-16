package com.expensetracker.controller;

import com.expensetracker.dao.DepartmentDAO;
import com.expensetracker.dao.EmployeeDAO;
import com.expensetracker.model.Department;
import com.expensetracker.model.Employee;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * Контроллер для формы добавления/редактирования сотрудника.
 */
public class EmployeeFormController {

    @FXML private TextField fullNameField;
    @FXML private ComboBox<Department> departmentComboBox;

    private Stage dialogStage;
    private Employee employee;
    private boolean isSaved = false;

    /**
     * Инициализация комбобокса с отделами.
     */
    @FXML
    private void initialize() {
        try {
            List<Department> departments = new DepartmentDAO().getAll();
            departmentComboBox.setItems(FXCollections.observableArrayList(departments));

            // Настройка отображения названия отдела в ComboBox
            departmentComboBox.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(Department item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            departmentComboBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Department item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });

        } catch (Exception e) {
            showError("Ошибка загрузки отделов", e.getMessage());
        }
    }

    /**
     * Устанавливает Stage текущего диалогового окна.
     */
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /**
     * Устанавливает сотрудника (в режиме редактирования).
     */
    public void setEmployee(Employee employee) {
        this.employee = employee;

        if (employee != null) {
            fullNameField.setText(employee.getFullName());

            Department empDept = employee.getDepartment();
            if (empDept != null) {
                for (Department dep : departmentComboBox.getItems()) {
                    if (dep.getId() == empDept.getId()) {
                        departmentComboBox.getSelectionModel().select(dep);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Обрабатывает кнопку "Сохранить".
     */
    @FXML
    private void handleSave() {
        String fullName = fullNameField.getText();
        Department selectedDept = departmentComboBox.getSelectionModel().getSelectedItem();

        if (fullName == null || fullName.isBlank() || selectedDept == null) {
            showError("Ошибка", "Пожалуйста, заполните все поля.");
            return;
        }

        try {
            EmployeeDAO dao = new EmployeeDAO();

            if (employee == null) {
                employee = new Employee();
            }

            employee.setFullName(fullName);
            employee.setDepartment(selectedDept);

            if (employee.getId() == 0) {
                dao.insert(employee);
            } else {
                dao.update(employee);
            }

            isSaved = true;
            dialogStage.close();

        } catch (Exception e) {
            showError("Ошибка сохранения", e.getMessage());
        }
    }

    /**
     * Обрабатывает кнопку "Отмена".
     */
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    /**
     * Возвращает флаг, была ли успешно сохранена форма.
     */
    public boolean isSaved() {
        return isSaved;
    }

    /**
     * Показывает сообщение об ошибке.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
