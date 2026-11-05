#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
java -cp 'bin/siscom.jar:lib/*' pe.edu.siscom.Inicio
