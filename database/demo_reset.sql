-- Explicitly destructive. Backup and stop web before running.
-- psql -v ON_ERROR_STOP=1 -v confirm_reset=RESET_FOOTBALL_DEMO -f database/demo_reset.sql
-- Optional -v demo_date=YYYY-MM-DD. No automatic startup reset.
\set ON_ERROR_STOP on
\if :{?confirm_reset}
SELECT :'confirm_reset' = 'RESET_FOOTBALL_DEMO' AS reset_authorized \gset
\else
\set reset_authorized false
\endif
\if :reset_authorized
BEGIN;
SELECT pg_advisory_xact_lock(20261105,27);
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
TRUNCATE TABLE goals,fouls,yellow_cards,red_cards,substitutions,events,
    match_players,match_clubs,club_players,matches,rounds,seasons,rules,
    clubs,stadiums,players,managers,staff,users,tournaments RESTART IDENTITY;
ALTER TABLE users DROP COLUMN IF EXISTS source_key;
ALTER TABLE rounds DROP COLUMN IF EXISTS round_number;
ALTER TABLE matches DROP COLUMN IF EXISTS match_number;
ALTER TABLE match_players DROP COLUMN IF EXISTS is_simulated;
ALTER TABLE events DROP COLUMN IF EXISTS is_simulated;
\ir demo_seed.sql
\ir demo_verify.sql
COMMIT;
\else
\echo 'Refusing reset: pass -v confirm_reset=RESET_FOOTBALL_DEMO after verifying backup.'
DO $$ BEGIN RAISE EXCEPTION 'Reset confirmation required: confirm_reset=RESET_FOOTBALL_DEMO'; END $$;
\endif
