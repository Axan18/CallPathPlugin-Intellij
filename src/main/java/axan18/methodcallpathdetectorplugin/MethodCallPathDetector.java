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
    private PsiMethod start; // field for easier testing

    @Override
    public void actionPerformed(AnActionEvent event) {
        PsiElement element = event.getData(CommonDataKeys.PSI_ELEMENT);
        if (!(element instanceof PsiMethod method)) // ensure the selected element is a method
            return;
        if (method.getBody() == null || method.getBody().getStatements().length == 0) {
            Messages.showMessageDialog("Method is empty", "Error", Messages.getErrorIcon());
            return;
        }
        start = method;
        Project project = event.getProject();
        String searchedMethodName = Messages.showInputDialog(
                project,
                "Method to find call path to",
                "Provide Name of the Method That You Want to Find Call Path To",
                Messages.getQuestionIcon()
        );
        if (searchedMethodName == null || searchedMethodName.trim().isEmpty()) {
            Messages.showMessageDialog("Method name not provided", "Warning", Messages.getWarningIcon());
            return;
        }
        assert project != null;
        List<PsiMethod> targets = ProgressManager.getInstance().runProcess(
                () -> ReadAction.compute(() -> Arrays.asList(
                        PsiShortNamesCache.getInstance(project)
                                .getMethodsByName(searchedMethodName, GlobalSearchScope.allScope(project))
                )),
                new EmptyProgressIndicator()
        );
        if (targets.isEmpty()) {
            Messages.showMessageDialog("Method " + searchedMethodName + " not found", "Error", Messages.getErrorIcon());
            return;
        }

        Set<PsiMethod> visited = new HashSet<>();
        for (PsiMethod target : targets) {
            List<List<String>> allPaths = ProgressManager.getInstance().runProcess(
                    () -> ReadAction.compute(() -> findCallPaths(target, new ArrayList<>(), visited)),
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
    List<List<String>> findCallPaths(PsiMethod callingMethod, List<String> path, Set<PsiMethod> visited) {
        List<List<String>> allPaths = new ArrayList<>();
        if (callingMethod == null || visited.contains(callingMethod)) {
            return allPaths;  // return empty if method is null or already visited
        }
        visited.add(callingMethod);
        path.add(callingMethod.getName());

        if (callingMethod.equals(start)) {
            List<String> fullPath = new ArrayList<>(path);
            fullPath.remove(0);  // remove the target method from the path
            Collections.reverse(fullPath);  // reverse the path to show start to target
            allPaths.add(fullPath);
        } else {
            // looking for references to the callers of the calling method
            Collection<PsiReference> references = ReferencesSearch.search(callingMethod).findAll();
            for (PsiReference reference : references) {
                PsiElement element = reference.getElement();
                if (isInsideThreadOrExecutor(element)) {
                    return allPaths; // if the method is inside a new thread, skip it
                }
                PsiMethod caller = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                if (caller != null && !visited.contains(caller)) {
                    List<List<String>> pathsFromCaller = findCallPaths(caller, new ArrayList<>(path), new HashSet<>(visited));
                    allPaths.addAll(pathsFromCaller);  // add the paths from this caller
                }
            }
        }
        return allPaths;
    }
    public void setStart(PsiMethod start) {
        this.start = start;
    }

    private boolean isInsideThreadOrExecutor(PsiElement element) {
        PsiElement parent = element;
        while (parent != null) {
            if (parent instanceof PsiNewExpression newExpr) {
                PsiJavaCodeReferenceElement ref = newExpr.getClassReference();
                if (ref != null && "java.lang.Thread".equals(ref.getQualifiedName())) {
                    return true;
                }
                if (isExecutorService(ref.getQualifiedName())) {
                    return true;
                }
            }
            if (parent instanceof PsiMethodCallExpression methodCall) {
                String methodName = methodCall.getMethodExpression().getReferenceName();
                if (isExecutorServiceMethod(methodName)) {
                    return true;
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isExecutorService(String className) {
        return "java.util.concurrent.ExecutorService".equals(className) ||
                "java.util.concurrent.Executors".equals(className);
    }

    private boolean isExecutorServiceMethod(String methodName) {
        return "execute".equals(methodName) || "submit".equals(methodName) ||
                "invokeAll".equals(methodName) || "invokeAny".equals(methodName);
    }

}
