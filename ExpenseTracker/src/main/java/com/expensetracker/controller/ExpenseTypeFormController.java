package com.expensetracker.controller;

import com.expensetracker.dao.ExpenseTypeDAO;
import com.expensetracker.model.ExpenseType;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;

public class ExpenseTypeFormController {

    @FXML
    private TextField nameField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private TextField limitField;

    private Stage dialogStage;
    private ExpenseType expenseType;
    private boolean isSaved = false;

    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    public void setExpenseType(ExpenseType expenseType) {
        this.expenseType = expenseType;

        if (expenseType != null) {
            nameField.setText(expenseType.getName());
            descriptionField.setText(expenseType.getDescription() != null ? expenseType.getDescription() : "");
            if (expenseType.getMonthlyLimit() != null) {
                limitField.setText(expenseType.getMonthlyLimit().toString());
            } else {
                limitField.setText("");
            }
        }
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();
        String limitText = limitField.getText().trim();

        if (name.isEmpty() || limitText.isEmpty()) {
            showError("Поля 'Название' и 'Лимит' обязательны.");
            return;
        }

        try {
            BigDecimal limit = new BigDecimal(limitText);

            if (limit.compareTo(BigDecimal.ZERO) < 0) {
                showError("Лимит должен быть положительным числом или равен нулю.");
                return;
            }

            ExpenseTypeDAO dao = new ExpenseTypeDAO();

            if (expenseType == null) {
                expenseType = new ExpenseType();
            }

            expenseType.setName(name);
            expenseType.setDescription(description.isEmpty() ? null : description);
            expenseType.setMonthlyLimit(limit);

            if (expenseType.getId() == 0) {
                dao.insert(expenseType);
            } else {
                dao.update(expenseType);
            }

            isSaved = true;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Успех");
            alert.setHeaderText(null);
            alert.setContentText("Тип расхода успешно сохранен.");
            alert.showAndWait();

            dialogStage.close();

        } catch (NumberFormatException e) {
            showError("Неверный формат лимита. Введите число.");
        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    public boolean isSaved() {
        return isSaved;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
