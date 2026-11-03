import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location('apk_inspector', Path(__file__).parents[1] / 'inspect-apk.py')
inspector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inspector)


class CompareApkTests(unittest.TestCase):
    def test_code_changes_do_not_hide_payload_regressions(self):
        with tempfile.TemporaryDirectory() as directory:
            reference, rebuilt = [Path(directory) / name for name in ('reference.apk', 'rebuilt.apk')]
            for path, dex in ((reference, b'original'), (rebuilt, b'refactored')):
                with ZipFile(path, 'w') as archive:
                    archive.writestr('AndroidManifest.xml', b'manifest')
                    archive.writestr('assets/player.json', b'config')
                    archive.writestr('classes.dex', dex)
            self.assertEqual(inspector.compare_payload(rebuilt, reference)['identical_payload_entries'], 2)
            with ZipFile(rebuilt, 'a') as archive:
                archive.writestr('lib/arm64-v8a/unexpected.so', b'new library')
            with self.assertRaisesRegex(ValueError, 'unexpected.so'):
                inspector.compare_payload(rebuilt, reference)


if __name__ == '__main__':
    unittest.main()
