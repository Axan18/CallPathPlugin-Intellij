package axan18.methodcallpathdetectorplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

public class MethodCallPathDetector extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        PsiElement element = event.getData(CommonDataKeys.PSI_ELEMENT);
        if (!(element instanceof PsiMethod start)) // ensure the selected element is a method
            return;
        if (isMethodEmpty(start)) { // stop if empty to avoid unnecessary processing
            Messages.showMessageDialog("Method is empty", "Error", Messages.getErrorIcon());
            return;
        }
        Project project = event.getProject();
        assert project != null;

        String searchedMethodName = getUserInput(project);
        if (notProvided(searchedMethodName)) {
            Messages.showMessageDialog("Method name not provided", "Warning", Messages.getWarningIcon());
            return;
        }
        List<PsiMethod> targets = getTargetMethods(project, searchedMethodName); // get all methods with the given name
        if (targets.isEmpty()) {
            Messages.showMessageDialog("Method " + searchedMethodName + " not found", "Error", Messages.getErrorIcon());
            return;
        }

        Set<PsiMethod> visited = new HashSet<>();
        for (PsiMethod target : targets) { // for each existing method with given name...
            List<List<String>> allPaths = ProgressManager.getInstance().runProcess(
                    () -> ReadAction.compute(() -> new PathFinder(start).findCallPaths(target, new ArrayList<>(), visited)),
                    new EmptyProgressIndicator());

            // Displaying all paths
            if (allPaths.isEmpty()) {
                Messages.showMessageDialog("No call path found for method " + searchedMethodName, "Info", Messages.getInformationIcon());
            } else {
                for (List<String> path : allPaths) {
                    Messages.showMessageDialog("Call path: " + String.join(" -> ", path),
                            "Call Path Found", Messages.getInformationIcon());
                }
            }
            visited.clear();
        }
    }

    private static boolean notProvided(String searchedMethodName) {
        return searchedMethodName == null || searchedMethodName.trim().isEmpty();
    }

    private static List<PsiMethod> getTargetMethods(Project project, String searchedMethodName) {
        return ProgressManager.getInstance().runProcess(
                () -> ReadAction.compute(() -> Arrays.asList(
                        PsiShortNamesCache.getInstance(project)
                                .getMethodsByName(searchedMethodName, GlobalSearchScope.allScope(project))
                )),
                new EmptyProgressIndicator()
        );
    }

    private static boolean isMethodEmpty(PsiMethod start) {
        return start.getBody() == null || start.getBody().getStatements().length == 0;
    }

    private String getUserInput(Project project) {
        return Messages.showInputDialog(
                project,
                "Method to find call path to",
                "Provide Name of the Method That You Want to Find Call Path To",
                Messages.getQuestionIcon()
        );
    }
}
