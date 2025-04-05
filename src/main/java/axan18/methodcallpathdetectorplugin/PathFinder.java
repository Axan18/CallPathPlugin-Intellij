package axan18.methodcallpathdetectorplugin;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

public class PathFinder {
    private PsiMethod start;
    PathFinder(PsiMethod start) {
        this.start = start;
    }
    PathFinder() {
        this.start = null;
    }

    /**
     * Sets the start method for finding call paths.
     *
     * @param start the starting method
     */
    public void setStart(PsiMethod start) {
        this.start = start;
    }

    /**
     * Finds all call paths backwards: from the specified calling method to the start method.
     *
     * @param currentMethod the method from which to start finding call paths and go up the call stack to the start method
     * @param path the current path of method names
     * @param visited the set of visited methods
     * @return a list of all call paths found
     */
    public List<List<String>> findCallPaths(PsiMethod currentMethod, List<String> path, Set<PsiMethod> visited) {
        List<List<String>> allPaths = new ArrayList<>();
        if (currentMethod == null || visited.contains(currentMethod)) {
            return allPaths;  // return empty if method is null or already visited
        }
        visited.add(currentMethod);
        path.add(currentMethod.getName());

        if (currentMethod.equals(start)) {
            List<String> fullPath = new ArrayList<>(path);
            fullPath.remove(0);  // remove the target method from the path
            Collections.reverse(fullPath);  // reverse the path to show start to target
            allPaths.add(fullPath);
        } else {
            // looking for references to the callers of the calling method
            Collection<PsiReference> references = ReferencesSearch.search(currentMethod).findAll();
            for (PsiReference reference : references) {
                PsiElement element = reference.getElement();
                if (isInsideThreadOrExecutor(element)) { // if the method is inside a new thread, skip it
                    return allPaths;
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

    /**
     * Checks if the specified element is inside a thread or executor service.
     *
     * @param element the element to check
     * @return true if the element is inside a thread or executor service, false otherwise
     */
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

    /**
     * Checks if the specified class name is an executor service.
     *
     * @param className the class name to check
     * @return true if the class name is an executor service, false otherwise
     */
    private boolean isExecutorService(String className) {
        return "java.util.concurrent.ExecutorService".equals(className) ||
                "java.util.concurrent.Executors".equals(className);
    }

    /**
     * Checks if the specified method name is an executor service method.
     *
     * @param methodName the method name to check
     * @return true if the method name is an executor service method, false otherwise
     */
    private boolean isExecutorServiceMethod(String methodName) {
        return "execute".equals(methodName) || "submit".equals(methodName) ||
                "invokeAll".equals(methodName) || "invokeAny".equals(methodName);
    }
}