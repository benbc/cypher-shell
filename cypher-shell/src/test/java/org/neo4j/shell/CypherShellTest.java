package org.neo4j.shell;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.Value;
import org.neo4j.shell.cli.CliArgHelper;
import org.neo4j.shell.cli.CliArgs;
import org.neo4j.shell.cli.StringShellRunner;
import org.neo4j.shell.commands.CommandExecutable;
import org.neo4j.shell.commands.CommandHelper;
import org.neo4j.shell.exception.CommandException;
import org.neo4j.shell.log.Logger;
import org.neo4j.shell.prettyprint.PrettyPrinter;
import org.neo4j.shell.state.BoltResult;
import org.neo4j.shell.state.BoltStateHandler;
import org.neo4j.shell.test.OfflineTestShell;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CypherShellTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    private BoltStateHandler mockedBoltStateHandler = mock(BoltStateHandler.class);
    private final PrettyPrinter mockedPrettyPrinter = mock(PrettyPrinter.class);
    private Logger logger = mock(Logger.class);
    private OfflineTestShell offlineTestShell;

    @Before
    public void setup() {
        doReturn(System.out).when(logger).getOutputStream();
        offlineTestShell = new OfflineTestShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);

        CommandHelper commandHelper = new CommandHelper(logger, Historian.empty, offlineTestShell);

        offlineTestShell.setCommandHelper(commandHelper);
    }

    @Test
    public void verifyDelegationOfConnectionMethods() throws CommandException {
        ConnectionConfig cc = new ConnectionConfig("", 1, "", "");
        CypherShell shell = new CypherShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);

        shell.connect(cc);
        verify(mockedBoltStateHandler).connect(cc);

        shell.isConnected();
        verify(mockedBoltStateHandler).isConnected();
    }

    @Test
    public void verifyDelegationOfResetMethod() throws CommandException {
        CypherShell shell = new CypherShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);

        shell.reset();
        verify(mockedBoltStateHandler).reset();
    }

    @Test
    public void verifyDelegationOfTransactionMethods() throws CommandException {
        CypherShell shell = new CypherShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);

        shell.beginTransaction();
        verify(mockedBoltStateHandler).beginTransaction();

        shell.commitTransaction();
        verify(mockedBoltStateHandler).commitTransaction();

        shell.rollbackTransaction();
        verify(mockedBoltStateHandler).rollbackTransaction();
    }

    @Test
    public void setWhenOfflineShouldThrow() throws CommandException {
        thrown.expect(CommandException.class);
        thrown.expectMessage("not connected");

        CypherShell shell = new OfflineTestShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);
        when(mockedBoltStateHandler.isConnected()).thenReturn(false);

        when(mockedBoltStateHandler.runCypher(anyString(), anyMap())).thenThrow(new CommandException("not connected"));

        shell.set("bob", "99");
    }

    @Test
    public void executeOfflineThrows() throws CommandException {
        thrown.expect(CommandException.class);
        thrown.expectMessage("Not connected to Neo4j");

        OfflineTestShell shell = new OfflineTestShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);
        when(mockedBoltStateHandler.isConnected()).thenReturn(false);

        shell.execute("RETURN 999");
    }

    @Test
    public void verifyVariableMethods() throws CommandException {
        Value value = mock(Value.class);
        Record recordMock = mock(Record.class);
        BoltResult boltResult = mock(BoltResult.class);

        when(mockedBoltStateHandler.runCypher(anyString(), anyMap())).thenReturn(Optional.of(boltResult));
        when(boltResult.getRecords()).thenReturn(Arrays.asList(recordMock));
        when(recordMock.get("bob")).thenReturn(value);
        when(value.asObject()).thenReturn("99");

        assertTrue(offlineTestShell.getAll().isEmpty());

        Optional result = offlineTestShell.set("bob", "99");
        assertEquals("99", result.get());
        assertEquals("99", offlineTestShell.getAll().get("bob"));

        offlineTestShell.remove("bob");
        assertTrue(offlineTestShell.getAll().isEmpty());
    }

    @Test
    public void executeShouldPrintResult() throws CommandException {
        Driver mockedDriver = mock(Driver.class);
        Session session = mock(Session.class);
        BoltResult result = mock(BoltResult.class);

        BoltStateHandler boltStateHandler = mock(BoltStateHandler.class);

        when(boltStateHandler.isConnected()).thenReturn(true);
        when(boltStateHandler.runCypher(anyString(), anyMap())).thenReturn(Optional.of(result));
        when(mockedPrettyPrinter.format(result)).thenReturn("999");
        when(mockedDriver.session()).thenReturn(session);

        OfflineTestShell shell = new OfflineTestShell(logger, boltStateHandler, mockedPrettyPrinter);
        shell.execute("RETURN 999");
        verify(logger).printOut(contains("999"));
    }

    @Test
    public void shouldParseCommandsAndArgs() {
        assertTrue(offlineTestShell.getCommandExecutable(":help").isPresent());
        assertTrue(offlineTestShell.getCommandExecutable(":help :set").isPresent());
        assertTrue(offlineTestShell.getCommandExecutable(":set \"A piece of string\"").isPresent());
    }

    @Test
    public void commandNameShouldBeParsed() {
        assertTrue(offlineTestShell.getCommandExecutable("   :help    ").isPresent());
        assertTrue(offlineTestShell.getCommandExecutable("   :help    \n").isPresent());
        assertTrue(offlineTestShell.getCommandExecutable("   :help   arg1 arg2 ").isPresent());
    }

    @Test
    public void incorrectCommandsThrowException() throws CommandException {
        thrown.expect(CommandException.class);
        thrown.expectMessage("Incorrect number of arguments");

        Optional<CommandExecutable> exe = offlineTestShell.getCommandExecutable("   :help   arg1 arg2 ");

        offlineTestShell.executeCmd(exe.get());
    }

    @Test
    public void shouldReturnNothingOnStrangeCommand() {
        Optional<CommandExecutable> exe = offlineTestShell.getCommandExecutable("   :aklxjde   arg1 arg2 ");

        assertFalse(exe.isPresent());
    }

    @Test
    public void specifyingACypherStringShouldGiveAStringRunner() throws IOException {
        CliArgs cliArgs = CliArgHelper.parse("MATCH (n) RETURN n");

        ShellRunner shellRunner = ShellRunner.getShellRunner(cliArgs, offlineTestShell, logger);

        if (!(shellRunner instanceof StringShellRunner)) {
            fail("Expected a different runner than: " + shellRunner.getClass().getSimpleName());
        }
    }

    @Test
    public void unsetAlreadyClearedValue() throws CommandException {
        // when
        // then
        assertFalse("Expected param to be unset", offlineTestShell.remove("unknown var").isPresent());
    }

    @Test
    public void setWithSomeBoltError() throws CommandException {
        // then
        thrown.expect(CommandException.class);
        thrown.expectMessage("Failed to set value of parameter");

        // given
        when(mockedBoltStateHandler.runCypher(anyString(), anyMap())).thenReturn(Optional.empty());

        CypherShell shell = new CypherShell(logger, mockedBoltStateHandler, mockedPrettyPrinter);

        // when
        shell.set("bob", "99");
    }
}
