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
import java.util.stream.Collectors;

public class MethodCallPathDetector extends AnAction {
    private PsiMethod start;

    @Override
    public void actionPerformed(AnActionEvent event) {
        PsiElement element = event.getData(CommonDataKeys.PSI_ELEMENT);
        if(!(element instanceof PsiMethod method)) // ensure the selected element is a method
            return;
        if (method.getBody() == null) {
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
            Messages.showMessageDialog("Method " + searchedMethodName +" not found", "Error", Messages.getErrorIcon());
            return;
        }

        Set<PsiMethod> visited = new HashSet<>();
        List<String> path;
        for (PsiMethod target : targets) {
            if (target.getBody() == null) {
                Messages.showMessageDialog(
                        "Method " + searchedMethodName + " is not called from " + start.getName() + " method",
                        "Error", Messages.getErrorIcon());
                return;
            }
            path = ProgressManager.getInstance().runProcess(
                    () -> ReadAction.compute(() ->
                            findCallPath(target, new ArrayList<>(), visited)),
                    new EmptyProgressIndicator());
            Messages.showMessageDialog("Call path: " +
                            String.join(" -> ", path),
                    "Call Path Found", Messages.getInformationIcon()
            );
        }
        }
    /**
     * Recursively finds the call path from the start method to the target method. Going backwards from the target method,
     * up through the call hierarchy to the start method.
     *
     * @param callingMethod  the target method to find the call path to
     * @param path    the current path of methods
     * @param visited the set of visited methods to avoid cycles
     */
    List<String> findCallPath(PsiMethod callingMethod, List<String> path, Set<PsiMethod> visited) {
        if (callingMethod == null || visited.contains(callingMethod))
            return path;
        visited.add(callingMethod);
        path.add(callingMethod.getName());

        if (callingMethod.equals(start)) {
            path.remove(0); // removing target method from the path
            Collections.reverse(path); // reversing the path to show the call path from start to target
            return path;
        }

        // looking for references to the callers of the calling method
        Collection<PsiReference> references = ReferencesSearch.search(callingMethod).findAll();
        for (PsiReference reference : references) {
            PsiElement element = reference.getElement();
            PsiMethod caller = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (caller != null){
                List<String> result = findCallPath(caller, new ArrayList<>(path), visited);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    public void setStart(PsiMethod start) {
        this.start = start;
    }
}
