create sequence chefsec_seq;
create sequence chefsec_secretary_seq;

CREATE TABLE chefsec (
    chefsec_id INT4 NOT NULL,
    enabled bool NOT NULL,
    cli_prefix VARCHAR(255),
    voicemail BOOL NOT NULL,
    fallback VARCHAR(255),
    chef_dialing VARCHAR(255) NOT NULL,
    expiration INT4 NOT NULL,
    USER_ID INT4 NOT NULL UNIQUE,
    PRIMARY KEY (chefsec_id)
);

CREATE TABLE chefsec_secretary (
    chefsec_secretary_id INT4 NOT NULL,
    forwarding_enabled BOOL NOT NULL,
    position INT4,
    enabled BOOL NOT NULL,
    expiration INT4 NOT NULL,
    ring_type VARCHAR(64) NOT NULL,
    chefsec_id INT4,
    user_id INT4 NOT NULL,
    schedule_id INT4,
    PRIMARY KEY (chefsec_secretary_id, user_id)
);


ALTER TABLE chefsec 
ADD CONSTRAINT fk_chefsec_chef_user_id 
FOREIGN KEY (user_id) 
REFERENCES users (user_id) 
MATCH FULL;

ALTER TABLE chefsec_secretary 
ADD CONSTRAINT fk_chefsec_secretary_chef_user_id 
FOREIGN KEY (chefsec_id) 
REFERENCES chefsec (chefsec_id) 
MATCH FULL;

ALTER TABLE chefsec_secretary 
ADD CONSTRAINT fk_chefsec_secretary_user_id 
FOREIGN KEY (user_id) 
REFERENCES users (user_id) 
MATCH FULL;

ALTER TABLE chefsec_secretary
ADD CONSTRAINT fk_chefec_secretary_schedule_id
FOREIGN KEY (schedule_id)
REFERENCES schedule (schedule_id)
MATCH FULL;

