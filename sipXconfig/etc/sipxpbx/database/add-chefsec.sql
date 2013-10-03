CREATE TABLE chefsec (
    chef_user_id INT4 NOT NULL,
    enabled bool NOT NULL,
    cli_prefix VARCHAR(255),
    voicemail BOOL NOT NULL,
    fallback VARCHAR(255),
	dial_chef VARCHAR(255),
	expiration INT4 NOT NULL,
    PRIMARY KEY (chef_user_id)
);

CREATE TABLE chefsec_secretary (
    chef_user_id INT4 NOT NULL,
	user_id INT4 NOT NULL,
	schedule_id INT4,
	forwarding_enabled BOOL NOT NULL,
	position INT4 NOT NULL,
	enabled BOOL NOT NULL,
	expiration INT4 NOT NULL,
	ring_type VARCHAR(64) NOT NULL,
    PRIMARY KEY (chef_user_id, user_id)
);

ALTER TABLE chefsec 
ADD CONSTRAINT fk_chefsec_chef_user_id 
FOREIGN KEY (chef_user_id) 
REFERENCES users (user_id) 
MATCH FULL;

ALTER TABLE chefsec_secretary 
ADD CONSTRAINT fk_chefsec_secretary_chef_user_id 
FOREIGN KEY (chef_user_id) 
REFERENCES chefsec (chef_user_id) 
MATCH FULL;

ALTER TABLE chefsec_secretary 
ADD CONSTRAINT fk_chefsec_secretary_user_id 
FOREIGN KEY (user_id) 
REFERENCES users (user_id) 
MATCH FULL;
