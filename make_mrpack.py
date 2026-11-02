#!/usr/bin/env python3
"""
Builds a Modrinth modpack (.mrpack) that the Modrinth App can import in one click.
It bundles: Minecraft 1.20.1 + Fabric Loader + Fabric API (fetched from Modrinth) + The Stalker jar.

Usage:  python3 tools/make_mrpack.py [path/to/the-stalker-x.y.z.jar]
Needs internet access (it asks the Modrinth API for Fabric API's download + hashes).
"""
import glob, hashlib, json, os, sys, urllib.parse, urllib.request, zipfile

MC, LOADER = "1.20.1", "0.15.11"
UA = "the-stalker-mrpack-builder/1.0 (local build script)"


def api(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def main():
    if len(sys.argv) > 1:
        jar = sys.argv[1]
    else:
        jars = [j for j in glob.glob("build/libs/the-stalker-*.jar") if not j.endswith(("-sources.jar", "-dev.jar"))]
        if not jars:
            sys.exit("No jar found in build/libs - run `gradle build` first or pass the jar path.")
        jar = sorted(jars)[-1]
    version = os.path.basename(jar)[len("the-stalker-"):-len(".jar")]

    q = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([MC])})
    fabric_api = api(f"https://api.modrinth.com/v2/project/fabric-api/version?{q}")[0]
    f = next((x for x in fabric_api["files"] if x.get("primary")), fabric_api["files"][0])

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": "The Stalker",
        "summary": "Psychological / liminal horror: something tall and pale is watching you.",
        "files": [{
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required", "server": "required"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        }],
        "dependencies": {"minecraft": MC, "fabric-loader": LOADER},
    }

    out = f"the-stalker-{version}.mrpack"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(jar, f"overrides/mods/{os.path.basename(jar)}")
    print("wrote", out, "(Fabric API:", f["filename"] + ")")


if __name__ == "__main__":
    main()
