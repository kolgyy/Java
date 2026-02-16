package com.expensetracker.controller;

import com.expensetracker.dao.DepartmentDAO;
import com.expensetracker.model.Department;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class DepartmentFormController {

    @FXML private TextField nameField;

    private Stage dialogStage;
    private Department department;
    private boolean isSaved = false;

    /**
     * Устанавливает stage диалога для закрытия.
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Устанавливает текущий объект Department (для редактирования).
     */
    public void setDepartment(Department department) {
        this.department = department;

        if (department != null) {
            nameField.setText(department.getName());
        }
    }

    /**
     * Обработка кнопки Сохранить.
     */
    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            showError("Название отдела не может быть пустым.");
            return;
        }

        try {
            DepartmentDAO dao = new DepartmentDAO();

            if (department == null) {
                department = new Department();
            }

            department.setName(name);

            if (department.getId() == 0) {
                dao.insert(department);
            } else {
                dao.update(department);
            }

            isSaved = true;
            dialogStage.close();

        } catch (Exception e) {
            showError("Ошибка при сохранении: " + e.getMessage());
        }
    }

    /**
     * Обработка кнопки Отмена.
     */
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    /**
     * Возвращает true, если данные были сохранены.
     */
    public boolean isSaved() {
        return isSaved;
    }

    /**
     * Показывает всплывающее окно с сообщением об ошибке.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }
}