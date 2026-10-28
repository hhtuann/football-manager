CREATE SEQUENCE IF NOT EXISTS player_code_seq START WITH 1;

SELECT setval(
               'player_code_seq',
               GREATEST(
                       COALESCE(
                               (SELECT MAX(SUBSTRING(username FROM 3)::BIGINT)
                                FROM users
                                WHERE username ~ '^CT[0-9]+$'),
                               0
                       ),
                       (SELECT last_value FROM player_code_seq)
               ),
               (SELECT EXISTS(SELECT 1 FROM users WHERE username ~ '^CT[0-9]+$'))
                   OR (SELECT is_called FROM player_code_seq)
       );

SELECT last_value, is_called
FROM player_code_seq;
