define(`repo_filename',`sipxecs.repo')
define(`repo_contents',
[sipXecs]
name=sipXecs for CentOS - \$basearch
baseurl=http://secure.telecats.nl/sipX/\$releasever/\$basearch
enabled=1
gpgcheck=0
)

define(`welcome_message',`
==========================
Welcome to Telecats sipXecs.

After logging in as root you will automatically be taken through a setup
procedure.
')
