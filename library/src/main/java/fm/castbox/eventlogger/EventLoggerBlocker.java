package fm.castbox.eventlogger;

public interface EventLoggerBlocker {
    boolean enable();

    boolean blockEventByName(String eventName);

    boolean blockUserPropertiesByName(String propertyName);

}
