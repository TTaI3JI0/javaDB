package exceptions;

/**
 * Единое непроверяемое исключение СУБД: ошибки синтаксиса, нарушения
 * ограничений (уникальность PK, типы), ошибки ввода-вывода.
 */
public class DbException extends RuntimeException {

    /**
     * @param message текст ошибки для пользователя
     */
    public DbException(String message) {
        super(message);
    }

    /**
     * @param message текст ошибки для пользователя
     * @param cause   исходная причина (например, {@link java.io.IOException})
     */
    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
