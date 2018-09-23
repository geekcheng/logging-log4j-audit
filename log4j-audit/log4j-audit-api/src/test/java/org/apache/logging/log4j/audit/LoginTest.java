package org.apache.logging.log4j.audit;

import org.apache.logging.log4j.audit.event.Login;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class LoginTest extends BaseEventTest {
    @Test
    public void testAuditClassToString() {
        Login event = LogEventFactory.getEvent(Login.class);
        event.setCompletionStatus("Success");
        String string = event.toString();

        assertEquals("[login completionStatus=\"Success\"]", string);
    }

    @Test
    public void testAuditClass() {
        Login event = LogEventFactory.getEvent(Login.class);

        event.logEvent();

        event.setCompletionStatus("Success");

        event.logEvent();

        List<String> msgs = app.getMessages();
        assertNotNull("No messages", msgs);
        assertTrue("No messages", msgs.size() == 2);
    }

    @Test
    public void testAuditLog() {
        LogEventFactory.logEvent(Login.class, null);

        LogEventFactory.logEvent(Login.class, Collections.emptyMap());
    }
}