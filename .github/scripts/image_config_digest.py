"""Read a single linux/amd64 image's exact config digest from docker save.

Do not use docker inspect .Id: the containerd store may return a manifest ID.
No archive members are extracted and config contents (including env) are not logged.
"""
import hashlib
import json
from pathlib import PurePosixPath
import re
import sys
import tarfile


def config_digest(stream):
    with tarfile.open(fileobj=stream, mode="r:*") as archive:
        def read_member(name, limit):
            member_path = PurePosixPath(name)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise ValueError("unsafe image archive member path")
            member = archive.getmember(name)
            if not member.isfile() or member.size > limit:
                raise ValueError("invalid image archive member type or size")
            with archive.extractfile(member) as stream:
                return stream.read(limit + 1)

        manifests = json.loads(read_member("manifest.json", 1024 * 1024))
        if not isinstance(manifests, list) or len(manifests) != 1:
            raise ValueError("expected exactly one saved image")
        config_path = manifests[0]["Config"]
        raw = read_member(config_path, 4 * 1024 * 1024)
        digest = hashlib.sha256(raw).hexdigest()
        # Validate both classic <config>.json and containerd blobs/sha256/<config>.
        expected = PurePosixPath(config_path).name.removesuffix(".json")
        if not re.fullmatch(r"[0-9a-f]{64}", expected) or digest != expected:
            raise ValueError("saved image config checksum mismatch")
        config = json.loads(raw)
        if (config.get("os"), config.get("architecture")) != ("linux", "amd64"):
            raise ValueError("expected linux/amd64 image config")
        return "sha256:" + digest


if __name__ == "__main__":
    try:
        print(config_digest(sys.stdin.buffer))
    except (OSError, ValueError, KeyError, TypeError, IndexError, tarfile.TarError) as error:
        print(f"::error::Cannot verify saved image config: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
