#!/usr/bin/env python3
#
# an fdroid plugin for setting up srclibs
#
# The 'fdroid build' gitlab-ci job uses --on-server, which does not
# set up the srclibs.  This plugin does the missing setup.

import argparse
import os
import subprocess
from fdroidserver import _, common, metadata
from copy import deepcopy
import re

fdroid_summary = 'add a new build entry for a CI run on gitlab CI'


def main():
    common.config = {
        'accepted_formats': 'yml',
        'sdk_path': os.getenv('ANDROID_HOME'),
    }
    common.fill_config_defaults(common.config)
    parser = argparse.ArgumentParser(usage="%(prog)s [options] [APPID[:VERCODE] [APPID[:VERCODE] ...]]")
    common.setup_global_opts(parser)
    parser.add_argument("appid", nargs='*', help=_("applicationId with optional versionCode in the form APPID[:VERCODE]"))
    metadata.add_metadata_arguments(parser)
    options = parser.parse_args()
    common.options = options
    pkgs = common.read_pkg_args(options.appid, True)
    allapps = metadata.read_metadata(xref=False)
    apps = common.read_app_args(options.appid, allapps, True)
    for appid, app in apps.items():
        app.Repo = "file://" + os.path.realpath("..")
        newbuild = deepcopy(app.builds[-1])
        version_name = subprocess.check_output(["git", "describe", "--tags", "--always"]).strip().decode()[1:]
        path = "../app/build.gradle"
        with open(path, 'r') as f:
            for line in f:
                matches = common.vcsearch_g(line)
                if matches:
                    version_code = matches.group(1)

        newbuild['versionName'] = version_name
        newbuild['versionCode'] = version_code
        newbuild['commit'] = os.getenv('CI_COMMIT_SHA')

        app.builds.append(newbuild)
        path = app.metadatapath
        base, ext = common.get_extension(path)
        metadata.write_metadata(base + '.' + ext, app)

if __name__ == "__main__":
    main()
