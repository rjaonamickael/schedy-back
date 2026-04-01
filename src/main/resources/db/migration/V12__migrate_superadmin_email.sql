-- Migrate superadmin email from schedy.io to schedy.work
UPDATE app_user SET email = 'superadmin@schedy.work' WHERE email = 'superadmin@schedy.io';
