import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('apk_inspector', ROOT / 'inspect-apk.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class InspectApkTests(unittest.TestCase):
    def test_rejects_missing_dex(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / 'invalid.apk'
            with ZipFile(apk, 'w') as z:
                z.writestr('AndroidManifest.xml', 'binary-placeholder')
                z.writestr('resources.arsc', 'binary-placeholder')
            with self.assertRaisesRegex(ValueError, 'classes.dex'):
                mod.inspect(apk, 'com.codesculptor.smartertube')

    def test_structurally_valid_zip(self):
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / 'fake.apk'
            with ZipFile(apk, 'w') as z:
                for name in ('AndroidManifest.xml', 'resources.arsc', 'classes.dex'):
                    z.writestr(name, 'test-only-content')
            with patch.dict('os.environ', {'ANDROID_HOME': '', 'ANDROID_SDK_ROOT': ''}):
                result = mod.inspect(apk, 'com.codesculptor.smartertube')
            self.assertEqual(len(result['sha256']), 64)

if __name__ == '__main__':
    unittest.main()
