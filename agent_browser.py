#!/usr/bin/env python3
"""
AGY Browser Controller - Bridge Antigravity AI to Android WebView
Uses the local CDP relay on 127.0.0.1:9334
"""

import sys
import os
import subprocess
import json

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CDP_MJS = os.path.join(SCRIPT_DIR, "cdp.mjs")

def run_cdp(*args):
    cmd = ["node", CDP_MJS] + list(args)
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return res.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"Error executing CDP command: {e.stderr.strip()}", file=sys.stderr)
        sys.exit(1)

def print_help():
    print("""
AGY Browser Controller
Commands:
  status                   Check if browser CDP endpoint is running
  nav <url>                Navigate browser to URL
  snapshot                 Extract title, URL, clean text, and interactive elements
  click <css_or_text>      Click an element (e.g. 'button.submit' or 'text=Login')
  type <text>              Type into focused element
  shot <output.png>        Capture full screenshot
  eval <javascript>        Execute custom JavaScript
""")

def main():
    if len(sys.argv) < 2 or sys.argv[1] in ["-h", "--help", "help"]:
        print_help()
        return

    action = sys.argv[1].lower()

    if action == "status":
        out = run_cdp("--list")
        print("🟢 Browser is online! Active pages:")
        print(out)
    elif action == "nav":
        if len(sys.argv) < 3:
            print("Usage: python3 agent_browser.py nav <url>")
            return
        url = sys.argv[2]
        print(f"Navigating to {url}...")
        print(run_cdp("--nav", url))
    elif action in ["snapshot", "dom"]:
        print(run_cdp("--snapshot"))
    elif action == "click":
        if len(sys.argv) < 3:
            print("Usage: python3 agent_browser.py click <selector>")
            return
        sel = sys.argv[2]
        print(run_cdp("--click", sel))
    elif action == "type":
        if len(sys.argv) < 3:
            print("Usage: python3 agent_browser.py type <text>")
            return
        text = " ".join(sys.argv[2:])
        print(run_cdp("--type", text))
    elif action in ["shot", "screenshot"]:
        outfile = sys.argv[2] if len(sys.argv) > 2 else "screenshot.png"
        print(run_cdp("--shot", outfile))
    elif action == "eval":
        if len(sys.argv) < 3:
            print("Usage: python3 agent_browser.py eval <js_code>")
            return
        code = " ".join(sys.argv[2:])
        print(run_cdp(code))
    else:
        # Pass raw flags directly to cdp.mjs
        print(run_cdp(*sys.argv[1:]))

if __name__ == "__main__":
    main()
