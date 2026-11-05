"""Handle the Python-trading checkbox, which MT5 doesn't document as an INI key.

Only used inside our dedicated terminal container. Locate controls by their text,
not desktop coordinates, and let the SDK verify the resulting permission.
"""
import ctypes
from ctypes import wintypes
import time


def terminal_running():
    """Don't let SDK initialize() launch a second terminal without /config."""
    user = ctypes.WinDLL("user32", use_last_error=True)
    callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user.GetClassNameW.argtypes = (wintypes.HWND, wintypes.LPWSTR, ctypes.c_int)
    found = []

    @callback_type
    def check(window, _):
        buffer = ctypes.create_unicode_buffer(256)
        user.GetClassNameW(window, buffer, len(buffer))
        if "MetaTrader::5" in buffer.value:
            found.append(window)
        return True

    user.EnumWindows(check, 0)
    return len(found) == 1


def enable_python_trading():
    user = ctypes.WinDLL("user32", use_last_error=True)
    callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user.SendMessageW.argtypes = (wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM)
    user.SendMessageW.restype = ctypes.c_ssize_t
    user.GetDlgItem.argtypes = (wintypes.HWND, ctypes.c_int)
    user.GetDlgItem.restype = wintypes.HWND
    user.SetForegroundWindow.argtypes = (wintypes.HWND,)
    user.GetWindowThreadProcessId.argtypes = (wintypes.HWND, ctypes.POINTER(wintypes.DWORD))

    def text(window, class_name=False):
        buffer = ctypes.create_unicode_buffer(512)
        function = user.GetClassNameW if class_name else user.GetWindowTextW
        function.argtypes = (wintypes.HWND, wintypes.LPWSTR, ctypes.c_int)
        function(window, buffer, len(buffer))
        return buffer.value

    def windows(parent=None):
        found = []

        @callback_type
        def collect(window, _):
            found.append(window)
            return True

        if parent is None:
            user.EnumWindows(collect, 0)
        else:
            user.EnumChildWindows.argtypes = (wintypes.HWND, callback_type, wintypes.LPARAM)
            user.EnumChildWindows(parent, collect, 0)
        return found

    def pid(window):
        value = wintypes.DWORD()
        user.GetWindowThreadProcessId(window, ctypes.byref(value))
        return value.value

    def keys(*codes):
        for code in codes:
            user.keybd_event(code, 0, 0, 0)
        for code in reversed(codes):
            user.keybd_event(code, 0, 2, 0)
        time.sleep(0.15)

    terminals = [window for window in windows() if "MetaTrader::5" in text(window, True)]
    if len(terminals) != 1:
        raise RuntimeError("Cannot identify the dedicated MT5 terminal window")
    terminal = terminals[0]
    user.SetForegroundWindow(terminal)
    keys(0x1B)  # Dismiss a startup dialog after the SDK has completed login.
    keys(0x11, 0x4F)  # Ctrl+O: platform Options.
    dialog = None
    for _ in range(30):
        candidates = [window for window in windows()
                      if pid(window) == pid(terminal) and text(window, True) == "#32770"
                      and any(text(child, True) == "SysTabControl32" for child in windows(window))]
        if len(candidates) == 1:
            dialog = candidates[0]
            break
        time.sleep(0.1)
    if dialog is None:
        raise RuntimeError("MT5 Options dialog did not open")
    try:
        for _ in range(16):
            controls = [child for child in windows(dialog)
                        if text(child, True) == "Button" and "python" in text(child).lower()]
            if len(controls) == 1:
                control = controls[0]
                if user.SendMessageW(control, 0x00F0, 0, 0) == 1:  # BM_GETCHECK
                    user.SendMessageW(control, 0x00F5, 0, 0)  # BM_CLICK
                user.SendMessageW(user.GetDlgItem(dialog, 1), 0x00F5, 0, 0)  # IDOK
                time.sleep(0.2)
                return
            user.SetForegroundWindow(dialog)
            keys(0x11, 0x09)  # Ctrl+Tab: next Options page.
        raise RuntimeError("MT5 Python-trading control was not found")
    except Exception:
        user.SendMessageW(user.GetDlgItem(dialog, 2), 0x00F5, 0, 0)  # IDCANCEL
        raise
