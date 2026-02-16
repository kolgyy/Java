package com.expensetracker.controller;

import com.expensetracker.dao.DepartmentDAO;
import com.expensetracker.dao.EmployeeDAO;
import com.expensetracker.dao.ExpenseTypeDAO;
import com.expensetracker.dao.PurchaseDAO;
import com.expensetracker.model.Department;
import com.expensetracker.model.Employee;
import com.expensetracker.model.ExpenseType;
import com.expensetracker.model.Purchase;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainController {

    @FXML private TableView<Department> departmentTable;
    @FXML private TableView<Employee> employeeTable;
    @FXML private TableView<ExpenseType> expenseTypeTable;
    @FXML private TableView<Purchase> purchaseTable;

    // Employees columns
    @FXML private TableColumn<Employee, String> employeeNameColumn;
    @FXML private TableColumn<Employee, String> employeeDepartmentColumn;

    // ExpenseTypes columns
    @FXML private TableColumn<ExpenseType, String> colExpenseTypeName;
    @FXML private TableColumn<ExpenseType, String> colExpenseTypeDescription;
    @FXML private TableColumn<ExpenseType, String> colExpenseTypeLimit;

    // Purchases columns
    @FXML private TableColumn<Purchase, String> colPurchaseEmployee;
    @FXML private TableColumn<Purchase, String> colPurchaseExpenseType;
    @FXML private TableColumn<Purchase, String> colPurchaseDate;
    @FXML private TableColumn<Purchase, String> colPurchaseAmount;
    @FXML private TableColumn<Purchase, String> colPurchaseReceipt;

    private final DepartmentDAO departmentDAO = new DepartmentDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ExpenseTypeDAO expenseTypeDAO = new ExpenseTypeDAO();
    private final PurchaseDAO purchaseDAO = new PurchaseDAO();

    private Stage mainStage;

    public void setMainStage(Stage stage) {
        this.mainStage = stage;
    }

    @FXML
    public void initialize() {
        setupDepartmentTable();
        loadDepartments();

        setupEmployeeTable();
        loadEmployees();

        setupExpenseTypeTable();
        loadExpenseTypes();

        setupPurchaseTable();
        loadPurchases();
    }

    // --- Departments ---

    private void setupDepartmentTable() {
        TableColumn<Department, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Department, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        departmentTable.getColumns().setAll(idCol, nameCol);
    }

    private void loadDepartments() {
        try {
            List<Department> list = departmentDAO.getAll();
            departmentTable.getItems().setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddDepartment() {
        showDepartmentForm(new Department());
    }

    @FXML
    private void handleEditDepartment() {
        Department selected = departmentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showDepartmentForm(selected);
        }
    }

    @FXML
    private void handleDeleteDepartment() {
        Department selected = departmentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                departmentDAO.delete(selected.getId());
                loadDepartments();
            } catch (SQLIntegrityConstraintViolationException e) {
                showError("Невозможно удалить отдел, так как в нём есть сотрудники.");
            } catch (SQLException e) {
                showError("Ошибка при удалении отдела: " + e.getMessage());
            }
        }
    }
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showDepartmentForm(Department department) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/expensetracker/view/DepartmentForm.fxml"));
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(department.getId() == 0 ? "Добавить отдел" : "Редактировать отдел");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mainStage);
            dialogStage.setScene(new Scene(page));

            DepartmentFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDepartment(department);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                loadDepartments();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Employees ---

    private void setupEmployeeTable() {
        employeeNameColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getFullName())
        );

        employeeDepartmentColumn.setCellValueFactory(
                cellData -> {
                    Department dept = cellData.getValue().getDepartment();
                    return new SimpleStringProperty(dept != null ? dept.getName() : "");
                }
        );
    }

    private void loadEmployees() {
        try {
            List<Employee> list = employeeDAO.getAll();
            employeeTable.getItems().setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshEmployeeTable() {
        loadEmployees();
    }

    @FXML
    private void handleAddEmployee() {
        showEmployeeForm(new Employee());
    }

    @FXML
    private void handleEditEmployee() {
        Employee selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showEmployeeForm(selected);
        }
    }

    @FXML
    private void handleDeleteEmployee() {
        Employee selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                employeeDAO.delete(selected.getId());
                refreshEmployeeTable();
            } catch (SQLIntegrityConstraintViolationException e) {
                showError("Невозможно удалить сотрудника, так как он связан с покупкой.");
            } catch (SQLException e) {
                showError("Ошибка при удалении отдела: " + e.getMessage());
            }
        }
    }

    private void showEmployeeForm(Employee employee) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/expensetracker/view/EmployeeForm.fxml"));
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(employee.getId() == 0 ? "Добавить сотрудника" : "Редактировать сотрудника");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mainStage);
            dialogStage.setScene(new Scene(page));

            EmployeeFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setEmployee(employee);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                refreshEmployeeTable();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Expense Types ---

    private void setupExpenseTypeTable() {
        colExpenseTypeName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colExpenseTypeDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colExpenseTypeLimit.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getMonthlyLimit().toString()));
    }

    private void loadExpenseTypes() {
        try {
            List<ExpenseType> list = expenseTypeDAO.getAll();
            expenseTypeTable.getItems().setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddExpenseType() {
        showExpenseTypeForm(new ExpenseType());
    }

    @FXML
    private void handleEditExpenseType() {
        ExpenseType selected = expenseTypeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showExpenseTypeForm(selected);
        }
    }

    @FXML
    private void handleDeleteExpenseType() {
        ExpenseType selected = expenseTypeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                expenseTypeDAO.delete(selected.getId());
                loadExpenseTypes();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showExpenseTypeForm(ExpenseType expenseType) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/expensetracker/view/ExpenseTypeForm.fxml"));
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(expenseType.getId() == 0 ? "Добавить вид расходов" : "Редактировать вид расходов");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mainStage);
            dialogStage.setScene(new Scene(page));

            ExpenseTypeFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setExpenseType(expenseType);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                loadExpenseTypes();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Purchases ---

    private void setupPurchaseTable() {
        colPurchaseEmployee.setCellValueFactory(cellData -> {
            Employee emp = cellData.getValue().getEmployee();
            return new SimpleStringProperty(emp != null ? emp.getFullName() : "");
        });
        colPurchaseExpenseType.setCellValueFactory(cellData -> {
            ExpenseType type = cellData.getValue().getExpenseType();
            return new SimpleStringProperty(type != null ? type.getName() : "");
        });
        colPurchaseDate.setCellValueFactory(cellData -> {
            if (cellData.getValue().getPurchaseDate() != null) {
                return new SimpleStringProperty(cellData.getValue().getPurchaseDate().format(DateTimeFormatter.ISO_DATE));
            } else {
                return new SimpleStringProperty("");
            }
        });
        colPurchaseAmount.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getAmount().toString()));
        colPurchaseReceipt.setCellValueFactory(new PropertyValueFactory<>("receiptNumber"));
    }

    private void loadPurchases() {
        try {
            List<Purchase> list = purchaseDAO.getAll();
            purchaseTable.getItems().setAll(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddPurchase() {
        showPurchaseForm(new Purchase());
    }

    @FXML
    private void handleEditPurchase() {
        Purchase selected = purchaseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showPurchaseForm(selected);
        }
    }

    @FXML
    private void handleDeletePurchase() {
        Purchase selected = purchaseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                purchaseDAO.delete(selected.getId());
                loadPurchases();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showPurchaseForm(Purchase purchase) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/expensetracker/view/PurchaseForm.fxml"));
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(purchase.getId() == 0 ? "Добавить покупку" : "Редактировать покупку");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mainStage);
            dialogStage.setScene(new Scene(page));

            PurchaseFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setPurchase(purchase);

            dialogStage.showAndWait();

            if (controller.isSaved()) {
                loadPurchases();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
