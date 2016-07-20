package org.neo4j.shell.commands;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.neo4j.shell.Command;
import org.neo4j.shell.Shell;
import org.neo4j.shell.exception.CommandException;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UnsetTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private Shell mockShell;
    private Command unsetCommand;

    @Before
    public void setup() {
        this.mockShell = mock(Shell.class);
        this.unsetCommand = new Unset(mockShell);
    }

    @Test
    public void shouldFailIfNoArgs() throws CommandException {
        thrown.expect(CommandException.class);
        thrown.expectMessage(containsString("Incorrect number of arguments"));

        unsetCommand.execute("");
        fail("Expected error");
    }

    @Test
    public void shouldFailIfMoreThanOneArg() throws CommandException {
        thrown.expect(CommandException.class);
        thrown.expectMessage(containsString("Incorrect number of arguments"));

        unsetCommand.execute("bob nob");
        fail("Expected error");
    }

    @Test
    public void unsetValue() throws CommandException {
        // given
        when(mockShell.isConnected()).thenReturn(true);
        HashMap<String, Object> value = new HashMap<>();
        value.put("bob", "9");
        when(mockShell.getQueryParams()).thenReturn(value);

        // when
        unsetCommand.execute("bob");
        // then
        assertTrue("Expected param to be unset", value.isEmpty());
    }

    @Test
    public void unsetAlreadyClearedValue() throws CommandException {
        // given
        when(mockShell.isConnected()).thenReturn(true);

        HashMap<String, Object> value = new HashMap<>();
        value.put("bob", "9");
        when(mockShell.getQueryParams()).thenReturn(value);

        // when
        unsetCommand.execute("nob");
        // then
        assertNull("Expected param to be unset", value.get("nob"));
    }
}