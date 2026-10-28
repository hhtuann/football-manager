CREATE TABLE users
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    full_name     VARCHAR(150) NOT NULL,
    dob           DATE,
    email         VARCHAR(254) UNIQUE,
    phone         VARCHAR(20)
);

CREATE TABLE managers
(
    user_id INTEGER PRIMARY KEY,

    CONSTRAINT fk_managers_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE staff
(
    user_id  INTEGER PRIMARY KEY,
    position VARCHAR(100) NOT NULL,

    CONSTRAINT fk_staff_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE players
(
    user_id          INTEGER PRIMARY KEY,

    nationality      VARCHAR(100)     NOT NULL,
    primary_position VARCHAR(100)     NOT NULL,

    height_cm        DOUBLE PRECISION NOT NULL,
    weight_kg        DOUBLE PRECISION NOT NULL,

    CONSTRAINT fk_players_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_player_height
        CHECK (height_cm > 0),

    CONSTRAINT chk_player_weight
        CHECK (weight_kg > 0)
);
