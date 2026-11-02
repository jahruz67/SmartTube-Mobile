#!/usr/bin/env python3
"""Targeted SmarterTube stmobile bottom-navigation implementation.

Source revision is pinned by the build workflow; this script checks exact integration
anchors before editing and refuses incompatible upstream changes. It never touches
SmartTube's TV code, content services, authentication, or player engine.
"""
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
JAVA_REL = Path('smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/ui/browse/MobileBrowseFragment.java')
LAYOUT_REL = Path('smarttubetv/src/stmobile/res/layout/mobile_browse_fragment.xml')
STRINGS_REL = Path('smarttubetv/src/stmobile/res/values/mobile_bottom_nav_strings.xml')
MARKER = 'SmartTube Mobile: persistent browse navigation.'

FIELDS = '''    /** Browse navigation only; all content continues through the upstream BrowsePresenter. */
    @Nullable
    private View[] mBottomTabs;
'''

BIND = '''        // The bottom bar links to the exact same section instances used by the drawer.
        // Do not create parallel content loaders or a second navigation back stack.
        mBottomTabs = new View[] {
                view.findViewById(R.id.bottom_home),
                view.findViewById(R.id.bottom_shorts),
                view.findViewById(R.id.bottom_subscriptions),
                view.findViewById(R.id.bottom_library),
                view.findViewById(R.id.bottom_search)
        };
        mBottomTabs[0].setOnClickListener(v -> selectBottomSection(MediaGroup.TYPE_HOME));
        mBottomTabs[1].setOnClickListener(v -> selectBottomSection(MediaGroup.TYPE_SHORTS));
        mBottomTabs[2].setOnClickListener(v -> selectBottomSection(MediaGroup.TYPE_SUBSCRIPTIONS));
        mBottomTabs[3].setOnClickListener(this::showLibraryMenu);
        mBottomTabs[4].setOnClickListener(v ->
                SearchPresenter.instance(requireContext()).startSearch(null));
'''

METHODS = '''    /** Navigate using the existing presenter and section adapter, preserving auth handling. */
    private void selectBottomSection(int sectionId) {
        if (mSectionAdapter == null) {
            return;
        }
        for (int i = 0; i < mSectionAdapter.getItemCount(); i++) {
            if (mSectionAdapter.getItem(i).getId() == sectionId) {
                selectSection(i, true);
                if (mContentList != null) {
                    mContentList.scrollToPosition(0);
                }
                return;
            }
        }
        // A user may have hidden a default section in SmartTube's sidebar settings.
        // Do not display a fake screen or silently navigate to the wrong content.
        if (mDrawer != null) {
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    /** Library is a real menu over existing History, Playlists and My videos sections. */
    private void showLibraryMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, MediaGroup.TYPE_HISTORY, 0, R.string.mobile_nav_history);
        popup.getMenu().add(0, MediaGroup.TYPE_USER_PLAYLISTS, 1, R.string.mobile_nav_playlists);
        popup.getMenu().add(0, MediaGroup.TYPE_MY_VIDEOS, 2, R.string.mobile_nav_my_videos);
        popup.setOnMenuItemClickListener(item -> {
            selectBottomSection(item.getItemId());
            return true;
        });
        popup.show();
    }

    /** Keep visual selection in sync when either the drawer or bottom bar navigates. */
    private void updateBottomSelection(int sectionId) {
        if (mBottomTabs == null) {
            return;
        }
        int selected = -1;
        if (sectionId == MediaGroup.TYPE_HOME) selected = 0;
        else if (sectionId == MediaGroup.TYPE_SHORTS) selected = 1;
        else if (sectionId == MediaGroup.TYPE_SUBSCRIPTIONS) selected = 2;
        else if (sectionId == MediaGroup.TYPE_HISTORY ||
                sectionId == MediaGroup.TYPE_USER_PLAYLISTS ||
                sectionId == MediaGroup.TYPE_MY_VIDEOS) selected = 3;
        for (int i = 0; i < mBottomTabs.length; i++) {
            View tab = mBottomTabs[i];
            boolean active = i == selected;
            tab.setSelected(active);
            int color = ContextCompat.getColor(requireContext(),
                    active ? R.color.brand_accent : R.color.mobile_text_secondary);
            ImageView icon = tab.findViewById(R.id.bottom_icon);
            TextView label = tab.findViewById(R.id.bottom_label);
            icon.setColorFilter(color);
            label.setTextColor(color);
        }
    }

'''


def require_once(source: str, needle: str, label: str) -> None:
    count = source.count(needle)
    if count != 1:
        raise ValueError(f'{label}: expected exactly one integration anchor, found {count}')


def patch_java(original: str) -> str:
    marker = 'private void updateBottomSelection(int sectionId)'
    if marker in original:
        if 'private void selectBottomSection(int sectionId)' not in original:
            raise ValueError('Partially applied Java patch detected')
        return original
    replacements = [
        ('import android.widget.ProgressBar;\n',
         'import android.widget.ProgressBar;\nimport android.widget.PopupMenu;\n', 'PopupMenu import'),
        ('import com.liskovsoft.mediaserviceinterfaces.oauth.Account;\n',
         'import com.liskovsoft.mediaserviceinterfaces.oauth.Account;\n'
         'import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;\n', 'MediaGroup import'),
        ('    private SectionAdapter mSectionAdapter;\n',
         '    private SectionAdapter mSectionAdapter;\n' + FIELDS, 'fields'),
        ('        mSectionAdapter = new SectionAdapter(this::onSectionPicked);\n',
         '        mSectionAdapter = new SectionAdapter(this::onSectionPicked);\n' + BIND,
         'bottom bar listener attachment'),
        ('        mSectionAdapter.setSelected(section);\n',
         '        mSectionAdapter.setSelected(section);\n'
         '        updateBottomSelection(section.getId());\n', 'selection synchronization'),
        ('    // ----- helpers -----\n', METHODS + '    // ----- helpers -----\n', 'methods'),
    ]
    result = original
    for before, after, label in replacements:
        require_once(result, before, label)
        result = result.replace(before, after, 1)
    return result


def patch_layout(original: str, nav: str) -> str:
    if MARKER in original:
        if '@+id/bottom_home' not in original:
            raise ValueError('Partially applied layout patch detected')
        return original
    seam = r'(        </FrameLayout>\n)(    </LinearLayout>\n\s*<!-- Drawer menu:)'
    if len(re.findall(seam, original)) != 1:
        raise ValueError('browse content/layout seam: expected exactly one integration anchor')
    return re.sub(seam, lambda match: match[1] + nav + match[2], original, count=1)


def apply(source_dir: Path, check_only: bool) -> None:
    java = source_dir / JAVA_REL
    layout = source_dir / LAYOUT_REL
    strings = source_dir / STRINGS_REL
    for path in (java, layout):
        if not path.is_file():
            raise ValueError(f'Not found: {path}. Need actual SmarterTube checkout.')
    nav = (ROOT / 'overlays/mobile_bottom_nav.xml').read_text(encoding='utf-8')
    nav_strings = (ROOT / 'overlays/mobile_bottom_nav_strings.xml').read_text(encoding='utf-8')
    ET.fromstring(nav_strings)
    old_java = java.read_text(encoding='utf-8')
    old_layout = layout.read_text(encoding='utf-8')
    new_java = patch_java(old_java)
    new_layout = patch_layout(old_layout, nav)
    ET.fromstring(new_layout)
    if strings.exists() and strings.read_text(encoding='utf-8') != nav_strings:
        raise ValueError(f'Existing resource collision: {strings}')
    files = ((java, old_java, new_java), (layout, old_layout, new_layout),
             (strings, nav_strings if strings.exists() else '', nav_strings))
    for path, old, new in files:
        if old != new or not path.exists():
            print(f'{"WOULD PATCH" if check_only else "PATCH"}: {path.relative_to(source_dir)}')
            if not check_only:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(new, encoding='utf-8')
        else:
            print(f'UNCHANGED: {path.relative_to(source_dir)}')
    if not check_only:
        digest = hashlib.sha256(new_java.encode('utf-8')).hexdigest()
        print(f'Java SHA256: {digest}')
        print('Integration applied. Compilation and device behavior NOT yet verified.')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('source_dir', type=Path, help='SmarterTube Git checkout root')
    p.add_argument('--check', action='store_true', help='Dry-run: validate anchors only')
    args = p.parse_args()
    try:
        apply(args.source_dir.resolve(), args.check)
    except (ValueError, OSError, ET.ParseError) as error:
        sys.exit(f'ERROR: {error}')
