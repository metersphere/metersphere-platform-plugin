package im.metersphere.plugin.exception;

import im.metersphere.plugin.utils.LogUtil;

import java.lang.reflect.Constructor;

public class MSPluginException extends RuntimeException {

    public static void throwException(String message) {
        throw getException(message);
    }

    public static RuntimeException getException(Object param) {
        try {

            // 转换成 MSException 由 MS 统一处理
            Class<?> clazz = MSPluginException.class
                    .getClassLoader()
                    .loadClass("io.metersphere.commons.exception.MSException");
            Constructor<?> constructor = clazz.getDeclaredConstructor(param == null ? String.class : param.getClass());
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(param);
            return (RuntimeException) instance;
        } catch (Exception e) {
            LogUtil.error(e);
        }
        return null;
    }

    public static void throwException(Throwable t) {
        throw getException(t);
    }
}
