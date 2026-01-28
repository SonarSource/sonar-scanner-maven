#!/usr/bin/env bash

main() {
  local command_log
  command_log=$(mvn help:describe --define cmd='sonar:sonar' --update-snapshots)
  local exit_code="${?}"
  if [[ "${exit_code}" -eq 0 ]]; then
    echo "################################################################################"
    echo "#                        The shorthand is still working                        #"
    echo "################################################################################"
    exit "${exit_code}"
  else
    echo "################################################################################" >&2
    echo "#                       The shorthand seems to be broken                       #" >&2
    echo "################################################################################" >&2
    echo "${command_log}" >&2
    exit "${exit_code}"
  fi
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
