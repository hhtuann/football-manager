package com.footballmanager.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class Cn2TournamentIntegrationTest {
    private Cn2PostgresFixture db;
    private TournamentDAO dao;

    @BeforeEach
    void initialize() throws Exception {
        db = new Cn2PostgresFixture();
        db.initialize();
        dao = new TournamentDAO() {
            @Override
            protected Connection getConnection() throws SQLException {
                return db.connect();
            }
        };
    }

    @AfterEach
    void cleanup() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void returnsTheOnlyConfiguredTournament() throws Exception {
        int expected = db.integer("SELECT id FROM tournaments");
        assertEquals(expected, dao.getTournament().getId());
        assertEquals("CN2 fixture", dao.getTournament().getName());
    }

    @Test
    void rejectsMissingTournamentInsteadOfReturningNull() throws Exception {
        // All cascading rows are inside the unique disposable schema, never public.
        db.execute("TRUNCATE tournaments CASCADE");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> dao.getTournament());
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }

    @Test
    void rejectsAmbiguousConfigurationInsteadOfSelectingFirstRow() throws Exception {
        db.insert("INSERT INTO tournaments(name) VALUES ('Second private tournament') RETURNING id");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> dao.getTournament());
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }
}
