-- NON-DESTRUCTIVE MIGRATION for the existing Football Manager database.
-- PREREQUISITE: database/schema.sql already created users, managers, staff, players.
-- This does NOT rename existing tables, delete data, or change existing columns.
BEGIN;
-- Football Manager: ERD extension (16 tables, related indexes, validation triggers, score view).
-- Names use PostgreSQL snake_case to match existing Java/DAO conventions.
-- Re-run supported: CREATE TABLE IF NOT EXISTS; existing data is NOT deleted.

-- A. Tournament / season / rules / rounds / fixtures
CREATE TABLE IF NOT EXISTS tournaments (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS rules (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    required_main_players INTEGER NOT NULL CHECK (required_main_players > 0),
    max_bench_players INTEGER NOT NULL CHECK (max_bench_players >= 0),
    max_substitutions INTEGER NOT NULL CHECK (max_substitutions >= 0),
    yellow_cards_to_suspend INTEGER NOT NULL CHECK (yellow_cards_to_suspend > 0),
    age_limit INTEGER NOT NULL CHECK (age_limit > 0),
    max_foreign_players INTEGER NOT NULL CHECK (max_foreign_players >= 0)
);

CREATE TABLE IF NOT EXISTS seasons (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (BTRIM(status) <> ''),
    tournament_id INTEGER NOT NULL REFERENCES tournaments (id) ON DELETE RESTRICT,
    rule_id INTEGER NOT NULL REFERENCES rules (id) ON DELETE RESTRICT,
    CONSTRAINT chk_season_dates CHECK (end_date >= start_date)
);

CREATE TABLE IF NOT EXISTS rounds (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (BTRIM(status) <> ''),
    season_id INTEGER NOT NULL REFERENCES seasons (id) ON DELETE RESTRICT,
    CONSTRAINT chk_round_dates CHECK (end_time >= start_time)
);

CREATE TABLE IF NOT EXISTS matches (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kickoff_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'ONGOING', 'FINISHED')),
    round_id INTEGER NOT NULL REFERENCES rounds (id) ON DELETE RESTRICT
);

-- B. Stadium / club
CREATE TABLE IF NOT EXISTS stadiums (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    address VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS clubs (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    short_name VARCHAR(10) NOT NULL,
    founding_year INTEGER,
    coach_name VARCHAR(100) NOT NULL,
    stadium_id INTEGER NOT NULL REFERENCES stadiums (id) ON DELETE RESTRICT,
    manager_id INTEGER NOT NULL UNIQUE REFERENCES managers (user_id) ON DELETE RESTRICT
);

-- C. Match participants and club membership
CREATE TABLE IF NOT EXISTS match_clubs (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role VARCHAR(10) NOT NULL CHECK (role IN ('HOME', 'AWAY')),
    match_id INTEGER NOT NULL REFERENCES matches (id) ON DELETE RESTRICT,
    club_id INTEGER NOT NULL REFERENCES clubs (id) ON DELETE RESTRICT,
    CONSTRAINT uq_match_club_role UNIQUE (match_id, role),
    CONSTRAINT uq_match_club_team UNIQUE (match_id, club_id)
);

CREATE TABLE IF NOT EXISTS club_players (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shirt_number INTEGER NOT NULL CHECK (shirt_number > 0),
    club_id INTEGER NOT NULL REFERENCES clubs (id) ON DELETE RESTRICT,
    player_id INTEGER NOT NULL REFERENCES players (user_id) ON DELETE RESTRICT,
    CONSTRAINT uq_club_player UNIQUE (club_id, player_id),
    CONSTRAINT uq_club_shirt UNIQUE (club_id, shirt_number)
);

CREATE TABLE IF NOT EXISTS match_players (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    registered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role VARCHAR(15) NOT NULL CHECK (role IN ('STARTING', 'SUBSTITUTE')),
    match_club_id INTEGER NOT NULL REFERENCES match_clubs (id) ON DELETE RESTRICT,
    club_player_id INTEGER NOT NULL REFERENCES club_players (id) ON DELETE RESTRICT,
    CONSTRAINT uq_match_registered_player UNIQUE (match_club_id, club_player_id)
);

-- D. Match event supertype + subtypes
CREATE TABLE IF NOT EXISTS events (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    minute INTEGER NOT NULL CHECK (minute >= 0),
    extra_minute INTEGER NOT NULL DEFAULT 0 CHECK (extra_minute >= 0),
    note VARCHAR(255),
    match_id INTEGER NOT NULL REFERENCES matches (id) ON DELETE RESTRICT,
    match_player_id INTEGER NOT NULL REFERENCES match_players (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS goals (
    id INTEGER PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    goal_type VARCHAR(15) NOT NULL CHECK (goal_type IN ('NORMAL', 'PENALTY', 'OWN')),
    assister_id INTEGER REFERENCES match_players (id) ON DELETE RESTRICT,
    CONSTRAINT chk_goal_assist_type CHECK (goal_type = 'NORMAL' OR assister_id IS NULL)
);

CREATE TABLE IF NOT EXISTS fouls (
    id INTEGER PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    victim_id INTEGER NOT NULL REFERENCES match_players (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS yellow_cards (
    id INTEGER PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS red_cards (
    id INTEGER PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS substitutions (
    id INTEGER PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    player_in_id INTEGER NOT NULL UNIQUE REFERENCES match_players (id) ON DELETE RESTRICT
);

-- FK lookup indexes (uniques already create their own indexes)
CREATE INDEX IF NOT EXISTS idx_seasons_tournament ON seasons (tournament_id);
CREATE INDEX IF NOT EXISTS idx_seasons_rule ON seasons (rule_id);
CREATE INDEX IF NOT EXISTS idx_rounds_season ON rounds (season_id);
CREATE INDEX IF NOT EXISTS idx_matches_round ON matches (round_id);
CREATE INDEX IF NOT EXISTS idx_clubs_stadium ON clubs (stadium_id);
CREATE INDEX IF NOT EXISTS idx_match_clubs_club ON match_clubs (club_id);
CREATE INDEX IF NOT EXISTS idx_club_players_player ON club_players (player_id);
CREATE INDEX IF NOT EXISTS idx_match_players_club_player ON match_players (club_player_id);
CREATE INDEX IF NOT EXISTS idx_events_match ON events (match_id);
CREATE INDEX IF NOT EXISTS idx_events_match_player ON events (match_player_id);
CREATE INDEX IF NOT EXISTS idx_goals_assister ON goals (assister_id);
CREATE INDEX IF NOT EXISTS idx_fouls_victim ON fouls (victim_id);

-- E. Cross-table checks that foreign keys alone cannot enforce.
CREATE OR REPLACE FUNCTION validate_match_player_club()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_club_in_match INTEGER;
    v_club_of_player INTEGER;
BEGIN
    SELECT club_id INTO v_club_in_match FROM match_clubs WHERE id = NEW.match_club_id;
    SELECT club_id INTO v_club_of_player FROM club_players WHERE id = NEW.club_player_id;
    IF v_club_in_match IS DISTINCT FROM v_club_of_player THEN
        RAISE EXCEPTION 'Registered player must belong to the club playing the match'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_match_player_club ON match_players;
CREATE TRIGGER trg_validate_match_player_club
BEFORE INSERT OR UPDATE OF match_club_id, club_player_id ON match_players
FOR EACH ROW EXECUTE FUNCTION validate_match_player_club();

CREATE OR REPLACE FUNCTION validate_event_match_player()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_player_match_id INTEGER;
BEGIN
    SELECT mc.match_id INTO v_player_match_id
    FROM match_players mp JOIN match_clubs mc ON mc.id = mp.match_club_id
    WHERE mp.id = NEW.match_player_id;
    IF v_player_match_id IS DISTINCT FROM NEW.match_id THEN
        RAISE EXCEPTION 'Event actor must be registered for the event match'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_event_match_player ON events;
CREATE TRIGGER trg_validate_event_match_player
BEFORE INSERT OR UPDATE OF match_id, match_player_id ON events
FOR EACH ROW EXECUTE FUNCTION validate_event_match_player();

CREATE OR REPLACE FUNCTION validate_goal_assister()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_actor_id INTEGER;
    v_actor_match_id INTEGER;
    v_actor_match_club_id INTEGER;
    v_assister_match_id INTEGER;
    v_assister_match_club_id INTEGER;
BEGIN
    IF NEW.assister_id IS NULL THEN RETURN NEW; END IF;

    SELECT e.match_player_id, e.match_id, mp.match_club_id
      INTO v_actor_id, v_actor_match_id, v_actor_match_club_id
    FROM events e JOIN match_players mp ON mp.id = e.match_player_id
    WHERE e.id = NEW.id;

    SELECT mc.match_id, mp.match_club_id
      INTO v_assister_match_id, v_assister_match_club_id
    FROM match_players mp JOIN match_clubs mc ON mc.id = mp.match_club_id
    WHERE mp.id = NEW.assister_id;

    IF NEW.assister_id = v_actor_id
       OR v_assister_match_id IS DISTINCT FROM v_actor_match_id
       OR v_assister_match_club_id IS DISTINCT FROM v_actor_match_club_id THEN
        RAISE EXCEPTION 'Assister must be a different player on the same team and match'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_goal_assister ON goals;
CREATE TRIGGER trg_validate_goal_assister
BEFORE INSERT OR UPDATE OF assister_id ON goals
FOR EACH ROW EXECUTE FUNCTION validate_goal_assister();

CREATE OR REPLACE FUNCTION validate_foul_victim()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_actor_id INTEGER;
    v_actor_match_id INTEGER;
    v_actor_match_club_id INTEGER;
    v_victim_match_id INTEGER;
    v_victim_match_club_id INTEGER;
BEGIN
    SELECT e.match_player_id, e.match_id, mp.match_club_id
      INTO v_actor_id, v_actor_match_id, v_actor_match_club_id
    FROM events e JOIN match_players mp ON mp.id = e.match_player_id
    WHERE e.id = NEW.id;

    SELECT mc.match_id, mp.match_club_id
      INTO v_victim_match_id, v_victim_match_club_id
    FROM match_players mp JOIN match_clubs mc ON mc.id = mp.match_club_id
    WHERE mp.id = NEW.victim_id;

    IF NEW.victim_id = v_actor_id
       OR v_victim_match_id IS DISTINCT FROM v_actor_match_id
       OR v_victim_match_club_id IS NOT DISTINCT FROM v_actor_match_club_id THEN
        RAISE EXCEPTION 'Foul victim must be an opponent in the same match'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_foul_victim ON fouls;
CREATE TRIGGER trg_validate_foul_victim
BEFORE INSERT OR UPDATE OF victim_id ON fouls
FOR EACH ROW EXECUTE FUNCTION validate_foul_victim();

CREATE OR REPLACE FUNCTION validate_substitution_player()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_actor_id INTEGER;
    v_actor_match_id INTEGER;
    v_actor_match_club_id INTEGER;
    v_in_match_id INTEGER;
    v_in_match_club_id INTEGER;
    v_in_role VARCHAR(15);
BEGIN
    SELECT e.match_player_id, e.match_id, mp.match_club_id
      INTO v_actor_id, v_actor_match_id, v_actor_match_club_id
    FROM events e JOIN match_players mp ON mp.id = e.match_player_id
    WHERE e.id = NEW.id;

    SELECT mc.match_id, mp.match_club_id, mp.role
      INTO v_in_match_id, v_in_match_club_id, v_in_role
    FROM match_players mp JOIN match_clubs mc ON mc.id = mp.match_club_id
    WHERE mp.id = NEW.player_in_id;

    IF NEW.player_in_id = v_actor_id
       OR v_in_match_id IS DISTINCT FROM v_actor_match_id
       OR v_in_match_club_id IS DISTINCT FROM v_actor_match_club_id
       OR v_in_role IS DISTINCT FROM 'SUBSTITUTE' THEN
        RAISE EXCEPTION 'Substitute must be a different registered bench player of the same team and match'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_substitution_player ON substitutions;
CREATE TRIGGER trg_validate_substitution_player
BEFORE INSERT OR UPDATE OF player_in_id ON substitutions
FOR EACH ROW EXECUTE FUNCTION validate_substitution_player();

-- Protect related fields against later edits that would bypass the above checks.
CREATE OR REPLACE FUNCTION protect_match_participant_links()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'match_clubs' THEN
        IF (OLD.match_id, OLD.club_id, OLD.role) IS DISTINCT FROM
           (NEW.match_id, NEW.club_id, NEW.role)
           AND EXISTS (SELECT 1 FROM match_players WHERE match_club_id = OLD.id) THEN
            RAISE EXCEPTION 'Cannot change team or match after registering match players'
                USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'club_players' THEN
        IF (OLD.club_id, OLD.player_id) IS DISTINCT FROM (NEW.club_id, NEW.player_id)
           AND EXISTS (SELECT 1 FROM match_players WHERE club_player_id = OLD.id) THEN
            RAISE EXCEPTION 'Cannot change registered player club membership'
                USING ERRCODE = '23514';
        END IF;
    ELSIF TG_TABLE_NAME = 'match_players' THEN
        IF (OLD.match_club_id, OLD.club_player_id, OLD.role) IS DISTINCT FROM
           (NEW.match_club_id, NEW.club_player_id, NEW.role)
           AND (EXISTS (SELECT 1 FROM events WHERE match_player_id = OLD.id)
           OR EXISTS (SELECT 1 FROM goals WHERE assister_id = OLD.id)
           OR EXISTS (SELECT 1 FROM fouls WHERE victim_id = OLD.id)
           OR EXISTS (SELECT 1 FROM substitutions WHERE player_in_id = OLD.id)) THEN
            RAISE EXCEPTION 'Cannot change match registration after event references it'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_match_club_link ON match_clubs;
CREATE TRIGGER trg_protect_match_club_link
BEFORE UPDATE OF match_id, club_id, role ON match_clubs
FOR EACH ROW EXECUTE FUNCTION protect_match_participant_links();

DROP TRIGGER IF EXISTS trg_protect_club_player_link ON club_players;
CREATE TRIGGER trg_protect_club_player_link
BEFORE UPDATE OF club_id, player_id ON club_players
FOR EACH ROW EXECUTE FUNCTION protect_match_participant_links();

DROP TRIGGER IF EXISTS trg_protect_match_player_link ON match_players;
CREATE TRIGGER trg_protect_match_player_link
BEFORE UPDATE OF match_club_id, club_player_id, role ON match_players
FOR EACH ROW EXECUTE FUNCTION protect_match_participant_links();

-- Editing the event actor/match after creating a typed event could invalidate
-- its assist, victim or substitution references.
CREATE OR REPLACE FUNCTION protect_typed_event_actor()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (OLD.match_id, OLD.match_player_id) IS DISTINCT FROM
       (NEW.match_id, NEW.match_player_id)
       AND (EXISTS (SELECT 1 FROM goals WHERE id = OLD.id)
         OR EXISTS (SELECT 1 FROM fouls WHERE id = OLD.id)
         OR EXISTS (SELECT 1 FROM yellow_cards WHERE id = OLD.id)
         OR EXISTS (SELECT 1 FROM red_cards WHERE id = OLD.id)
         OR EXISTS (SELECT 1 FROM substitutions WHERE id = OLD.id)) THEN
        RAISE EXCEPTION 'Cannot change match or actor after creating an event subtype'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_typed_event_actor ON events;
CREATE TRIGGER trg_protect_typed_event_actor
BEFORE UPDATE OF match_id, match_player_id ON events
FOR EACH ROW EXECUTE FUNCTION protect_typed_event_actor();

-- Enforce exactly HOME + AWAY when moving to ONGOING or FINISHED.
-- A SCHEDULED match can be created before both clubs are assigned.
CREATE OR REPLACE FUNCTION validate_match_starting_clubs()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_count INTEGER;
BEGIN
    IF NEW.status IN ('ONGOING', 'FINISHED') THEN
        SELECT COUNT(*) INTO v_count FROM match_clubs WHERE match_id = NEW.id;
        IF v_count <> 2 THEN
            RAISE EXCEPTION 'An ongoing/finished match needs both HOME and AWAY clubs'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_match_starting_clubs ON matches;
CREATE TRIGGER trg_validate_match_starting_clubs
BEFORE INSERT OR UPDATE OF status ON matches
FOR EACH ROW EXECUTE FUNCTION validate_match_starting_clubs();

CREATE OR REPLACE FUNCTION protect_live_match_clubs()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_status VARCHAR(20);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (OLD.match_id, OLD.club_id, OLD.role) IS NOT DISTINCT FROM
           (NEW.match_id, NEW.club_id, NEW.role) THEN
            RETURN NEW;
        END IF;
        IF EXISTS (SELECT 1 FROM matches
                   WHERE id IN (OLD.match_id, NEW.match_id)
                     AND status IN ('ONGOING', 'FINISHED')) THEN
            RAISE EXCEPTION 'Cannot modify participating clubs after a match starts'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        SELECT status INTO v_status FROM matches WHERE id = OLD.match_id;
    ELSE
        SELECT status INTO v_status FROM matches WHERE id = NEW.match_id;
    END IF;
    IF v_status IN ('ONGOING', 'FINISHED') THEN
        RAISE EXCEPTION 'Cannot modify participating clubs after a match starts'
            USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_live_match_clubs ON match_clubs;
CREATE TRIGGER trg_protect_live_match_clubs
BEFORE INSERT OR UPDATE OF match_id, club_id, role OR DELETE ON match_clubs
FOR EACH ROW EXECUTE FUNCTION protect_live_match_clubs();

-- F. Derived score from goals (own goals count for the opposing team).
-- This intentionally does not add home_score/away_score columns absent from the ERD.
CREATE OR REPLACE VIEW v_match_scores AS
SELECT m.id AS match_id,
       COALESCE(SUM(CASE WHEN g.id IS NOT NULL AND
                           ((mc.role = 'HOME' AND g.goal_type <> 'OWN') OR
                            (mc.role = 'AWAY' AND g.goal_type = 'OWN'))
                         THEN 1 ELSE 0 END), 0)::INTEGER AS home_score,
       COALESCE(SUM(CASE WHEN g.id IS NOT NULL AND
                           ((mc.role = 'AWAY' AND g.goal_type <> 'OWN') OR
                            (mc.role = 'HOME' AND g.goal_type = 'OWN'))
                         THEN 1 ELSE 0 END), 0)::INTEGER AS away_score
FROM matches m
LEFT JOIN events e ON e.match_id = m.id
LEFT JOIN goals g ON g.id = e.id
LEFT JOIN match_players mp ON mp.id = e.match_player_id
LEFT JOIN match_clubs mc ON mc.id = mp.match_club_id
GROUP BY m.id;

-- NOTE: The two-club minimum is required when starting/finishing, not SCHEDULED.
-- Lineup size limits, bookings/suspensions,
-- date containment (season/round/match), and cross-table event subtype exclusivity
-- require further transaction/business logic; FK/CHECK alone cannot guarantee them.

COMMIT;
