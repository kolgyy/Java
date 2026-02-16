package com.expensetracker.controller;

import com.expensetracker.dao.EmployeeDAO;
import com.expensetracker.dao.ExpenseTypeDAO;
import com.expensetracker.dao.PurchaseDAO;
import com.expensetracker.model.Employee;
import com.expensetracker.model.ExpenseType;
import com.expensetracker.model.Purchase;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PurchaseFormController {

    @FXML
    private ComboBox<Employee> employeeComboBox;
    @FXML
    private ComboBox<ExpenseType> expenseTypeComboBox;
    @FXML
    private DatePicker purchaseDatePicker;
    @FXML
    private TextField amountField;
    @FXML
    private TextField receiptNumberField;

    private Stage dialogStage;
    private Purchase purchase;
    private boolean isSaved = false;

    @FXML
    private void initialize() {
        try {
            employeeComboBox.setItems(FXCollections.observableArrayList(new EmployeeDAO().getAll()));
            expenseTypeComboBox.setItems(FXCollections.observableArrayList(new ExpenseTypeDAO().getAll()));

            employeeComboBox.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(Employee item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFullName());
                }
            });
            employeeComboBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Employee item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFullName());
                }
            });

            expenseTypeComboBox.setCellFactory(param -> new ListCell<>() {
                @Override
                protected void updateItem(ExpenseType item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
            expenseTypeComboBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(ExpenseType item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });

            purchaseDatePicker.setValue(LocalDate.now());

            purchaseDatePicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate date, boolean empty) {
                    super.updateItem(date, empty);
                    if (date.isAfter(LocalDate.now())) {
                        setDisable(true);
                    }
                }
            });

        } catch (Exception e) {
            showError("Ошибка загрузки данных: " + e.getMessage());
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setPurchase(Purchase purchase) {
        if (purchase == null) {
            purchase = new Purchase();
        }
        this.purchase = purchase;

        amountField.setText(purchase.getAmount() != null ? purchase.getAmount().toString() : "");
        receiptNumberField.setText(purchase.getReceiptNumber() != null ? purchase.getReceiptNumber() : "");
        purchaseDatePicker.setValue(purchase.getPurchaseDate() != null ? purchase.getPurchaseDate() : LocalDate.now());

        if (purchase.getEmployeeId() != 0) {
            for (Employee emp : employeeComboBox.getItems()) {
                if (emp.getId() == purchase.getEmployeeId()) {
                    employeeComboBox.getSelectionModel().select(emp);
                    break;
                }
            }
        }

        if (purchase.getExpenseTypeId() != 0) {
            for (ExpenseType type : expenseTypeComboBox.getItems()) {
                if (type.getId() == purchase.getExpenseTypeId()) {
                    expenseTypeComboBox.getSelectionModel().select(type);
                    break;
                }
            }
        }
    }

    @FXML
    private void handleSave() {
        try {
            Employee emp = employeeComboBox.getValue();
            ExpenseType type = expenseTypeComboBox.getValue();
            LocalDate date = purchaseDatePicker.getValue();
            String amountText = amountField.getText().trim();
            String receipt = receiptNumberField.getText().trim();

            if (emp == null || type == null || date == null || amountText.isEmpty()) {
                showError("Пожалуйста, заполните все обязательные поля.");
                return;
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(amountText);
            } catch (NumberFormatException e) {
                showError("Сумма должна быть числом.");
                return;
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Сумма должна быть положительным числом.");
                return;
            }

            if (purchase == null) {
                purchase = new Purchase();
            }

            purchase.setEmployeeId(emp.getId());
            purchase.setExpenseTypeId(type.getId());
            purchase.setPurchaseDate(date);
            purchase.setAmount(amount);
            purchase.setReceiptNumber(receipt.isEmpty() ? null : receipt);

            PurchaseDAO dao = new PurchaseDAO();

            BigDecimal limit = type.getMonthlyLimit();
            BigDecimal spent = dao.getSumAmountByExpenseTypeAndMonth(
                    type.getId(), date.getYear(), date.getMonthValue(), purchase.getId());
            BigDecimal total = spent.add(amount);

            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0 && total.compareTo(limit) > 0) {
                showError("Превышен месячный лимит по выбранному виду расхода. Лимит: " + limit + ", уже потрачено: " + spent);
                return;
            }

            if (purchase.getId() == 0) {
                dao.insert(purchase);
            } else {
                dao.update(purchase);
            }

            isSaved = true;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Успех");
            alert.setHeaderText(null);
            alert.setContentText("Покупка успешно сохранена.");
            alert.showAndWait();

            dialogStage.close();

        } catch (Exception e) {
            showError("Ошибка при сохранении: " + e.getMessage());
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
