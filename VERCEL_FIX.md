# Fix for Vercel Deployment Error

## Problem
Vercel is trying to use `react-scripts` instead of Vite, causing the error:
```
sh: line 1: react-scripts: command not found
Error: Command "react-scripts build" exited with 12
```

## Solutions

### Option 1: Deploy Frontend Folder Only (Recommended)

1. In Vercel dashboard, go to your project settings
2. Set **Root Directory** to `frontend`
3. The `frontend/vercel.json` will be used automatically

### Option 2: Update Vercel Project Settings

If deploying from root, configure these settings in Vercel:

**Framework Preset:** Vite
**Root Directory:** frontend
**Build Command:** `npm run build`
**Output Directory:** `dist`
**Install Command:** `npm install`

### Option 3: Add package.json Override

Create/update `frontend/package.json` to ensure Vite is the build tool:
```json
{
  "scripts": {
    "build": "vite build",
    "dev": "vite"
  }
}
```

### Option 4: Environment Variable

In Vercel dashboard → Settings → Environment Variables:
- Add: `FRAMEWORK=vite`

## Quick Fix Steps

1. **Delete any `package-lock.json` that might reference react-scripts**
2. **Ensure `frontend/package.json` has:**
   ```json
   "scripts": {
     "build": "vite build"
   }
   ```
3. **Redeploy in Vercel**

The updated `frontend/vercel.json` should now work correctly.

