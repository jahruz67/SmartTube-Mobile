"""Validates the instrumented test is installed in a flavor-specific source set."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('smoke', ROOT / 'scripts/install-mobile-smoke-test.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class InstrumentationSourceTests(unittest.TestCase):
    def test_installation_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            mod.install(root)
            target = root / mod.REL
            self.assertTrue(target.exists())
            original = target.read_bytes()
            mod.install(root)
            self.assertEqual(original, target.read_bytes())
            self.assertIn('androidTestStmobile/java', mod.REL.as_posix())
            target.write_text('conflict')
            with self.assertRaises(ValueError):
                mod.install(root)

    def test_overlay_has_five_accessible_tabs(self):
        # Wrap fragment overlay for XML parsing, do not mistake this for AAPT validation.
        s = (ROOT / 'overlays/mobile_bottom_nav.xml').read_text()
        root = ET.fromstring('<root xmlns:android="http://schemas.android.com/apk/res/android">' + s + '</root>')
        bar = root[0]
        self.assertEqual(5, len(bar))
        ns = '{http://schemas.android.com/apk/res/android}'
        for tab in bar:
            self.assertIn('mobile_nav_', tab.attrib[ns + 'contentDescription'])
            self.assertEqual('yes', tab.attrib[ns + 'importantForAccessibility'])
            self.assertEqual('true', tab.attrib[ns + 'clickable'])
            self.assertEqual('no', tab[0].attrib[ns + 'importantForAccessibility'])
            self.assertEqual('no', tab[1].attrib[ns + 'importantForAccessibility'])

if __name__ == '__main__':
    unittest.main()
