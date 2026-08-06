import builtins
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import release


class ReleaseToolTest(unittest.TestCase):
    def test_local_dictionary_readme_is_read_as_utf8(self):
        with tempfile.TemporaryDirectory() as tmp:
            readme = Path(tmp) / "README.md"
            # U+0181 encodes to byte 0x81 in UTF-8's continuation position; Windows cp1252
            # rejects that byte when the file is opened without an explicit UTF-8 encoding.
            readme.write_text("# Dictionaries\n| Ɓengali | [dict](main_bn.dict) |\n", encoding="utf-8")
            real_open = builtins.open

            def redirect_open(path, *args, **kwargs):
                if path == "../dictionaries/README.md":
                    if kwargs.get("encoding", "").lower() != "utf-8":
                        raise UnicodeDecodeError("charmap", b"\x81", 0, 1, "undefined")
                    return real_open(readme, *args, **kwargs)
                return real_open(path, *args, **kwargs)

            with patch.object(release.os.path, "isfile", return_value=True), \
                    patch("builtins.open", side_effect=redirect_open):
                lines = release.read_dicts_readme()

            self.assertIn("Ɓengali", "".join(lines))


if __name__ == "__main__":
    unittest.main()
