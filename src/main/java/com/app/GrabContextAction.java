package com.app;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

public class GrabContextAction extends AnAction {
    String PACKAGE_NAME = "kirkodevv/context-grab";

    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null) {
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);

        // Check if JavaScript plugin is available
        try {
            Class<?> jsFunctionClass = Class.forName("com.intellij.lang.javascript.psi.JSFunction");
            PsiElement function = PsiTreeUtil.getParentOfType(element, (Class<? extends PsiElement>) jsFunctionClass);

            if (function != null) {
                // Use reflection to get the name of the function
                String functionName = (String) jsFunctionClass.getMethod("getName").invoke(function);
                functionName = functionName != null ? functionName : "anonymous";
                this.getFunctionContext(e, functionName);
            } else {
                Messages.showWarningDialog(project, "No function found at cursor position", "Grab Context");
            }
        } catch (ClassNotFoundException ex) {
            Messages.showErrorDialog(project, "JavaScript support is not available", "Grab Context");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "An error occurred: " + ex.getMessage(), "Grab Context");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(project != null && editor != null);
    }

    private String getPackageManager(String projectPath) {
        boolean isYarn = new File(projectPath, "yarn.lock").exists();
        boolean isNpm = new File(projectPath, "package-lock.json").exists();

        String command = "";
        if (isYarn) {
            command = "yarn";
        } else if (isNpm) {
            command = "npm";
        }

        return command;
    }

    private void packageCheck(Project project, String projectPath, String manager) {
        boolean isYarn = Objects.equals(manager, "yarn");

        File nodeModules = new File(projectPath, "node_modules/" + PACKAGE_NAME);
        if (!nodeModules.exists()) {
            int result = Messages.showYesNoDialog(project,
                    "The context-grab package is not installed. Would you like to install it?",
                    "Grab Context",
                    "Install", "Cancel", null);

            if (result != Messages.YES) return;

            String installCommand = isYarn ?
                    "yarn add --dev " + PACKAGE_NAME :
                    "npm install --save-dev " + PACKAGE_NAME;

            try {
                Process p = Runtime.getRuntime().exec(installCommand, null, new File(projectPath));
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    Messages.showErrorDialog(project, "Failed to install context-grab", "Grab Context");
                    return;
                }
            } catch (IOException | InterruptedException ex) {
                Messages.showErrorDialog(project, "Error installing context-grab: " + ex.getMessage(), "Grab Context");
                return;
            }
        }
    }

    private void getFunctionContext(@NotNull AnActionEvent e, String functionName) throws IOException {
        Project project = e.getProject();
        if (project == null) return;

        String projectPath = project.getBasePath();
        if (projectPath == null) return;

        // get package manager choice
        String pm = this.getPackageManager(projectPath);
        if (Objects.equals(pm, "")) {
            Messages.showErrorDialog(project, "No yarn.lock or package-lock.json found", "Grab Context");
            return;
        }

        boolean isYarn = Objects.equals(pm, "yarn");

        // make sure package is installed
        this.packageCheck(project, projectPath, pm);

        String filePath = e.getData(CommonDataKeys.VIRTUAL_FILE).getPath();
        String relativeFilePath = filePath.substring(projectPath.length() + 1);


        String packagePath = projectPath + "/node_modules/" + PACKAGE_NAME;
        File packageFile = new File(packagePath);
        String resolvedPath = packageFile.getCanonicalPath();

        String command = pm + " run " + " start " + projectPath + " " + relativeFilePath + " " + functionName;
        this.executeCommand(command, project, resolvedPath);
    }

    private void executeCommand(String command, Project project, String projectPath) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Grabbing Context", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
                    pb.directory(new File(projectPath));
                    pb.redirectErrorStream(true);
                    Process p = pb.start();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    int exitCode = p.waitFor();
                    String finalOutput = output.toString();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (exitCode == 0) {
                            Messages.showInfoMessage(project, "Context grabbed successfully", "Grab Context");
                        } else {
                            Messages.showErrorDialog(project, "Error grabbing context:\nCommand: " + command + "\nOutput:\n" + finalOutput, "Grab Context");
                        }
                    });
                } catch (IOException | InterruptedException ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showErrorDialog(project, "Error executing command: " + ex.getMessage(), "Grab Context")
                    );
                }
            }
        });
    }
}