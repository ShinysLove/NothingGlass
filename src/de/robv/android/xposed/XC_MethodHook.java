package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public int priority;
    public XC_MethodHook() { this.priority = 50; }
    public XC_MethodHook(int priority) { this.priority = priority; }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; this.throwable = null; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable throwable) { this.throwable = throwable; this.result = null; }
        public Object getObjectExtra(String key) { return null; }
        public void setObjectExtra(String key, Object value) {}
    }

    public static class Unhook {
        public Member getHookedMethod() { return null; }
        public void unhook() {}
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
