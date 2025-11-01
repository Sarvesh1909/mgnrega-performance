# Deploy MGNREGA App to Vercel

This guide explains how to deploy the MGNREGA application using Vercel for the frontend and a backend service (Railway, Render, or Fly.io) for the Spring Boot API.

## Architecture

- **Frontend**: Vercel (React/Vite)
- **Backend**: Railway/Render/Fly.io (Spring Boot + PostgreSQL)
- **Database**: PostgreSQL (provided by backend hosting service)

## Step 1: Deploy Backend First

The backend needs to be deployed first because the frontend will connect to it.

### Option A: Deploy to Railway (Recommended)

1. **Sign up/Login**: Go to [Railway.app](https://railway.app)

2. **Create New Project**:
   - Click "New Project"
   - Select "Deploy from GitHub repo" (or upload code)

3. **Add PostgreSQL Database**:
   - Click "+ New" → "Database" → "Add PostgreSQL"
   - Note the connection details (you'll need these)

4. **Add Spring Boot Service**:
   - Click "+ New" → "GitHub Repo" → Select your repo
   - Select the `backend` folder
   - Railway will auto-detect it's a Java app

5. **Configure Environment Variables**:
   ```
   DATABASE_URL=jdbc:postgresql://[railway-provided-url]:5432/railway
   DATABASE_USER=postgres
   DATABASE_PASSWORD=[railway-provided-password]
   DATAGOV_API_KEY=your_api_key_here
   SERVER_PORT=8080
   USE_DATABASE=true
   SPRING_PROFILES_ACTIVE=prod
   ```

6. **Deploy Settings**:
   - Build Command: `mvn clean package -DskipTests`
   - Start Command: `java -jar target/backend-0.0.1-SNAPSHOT.jar`
   - Root Directory: `backend`

7. **Get Backend URL**:
   - Once deployed, Railway provides a URL like: `https://your-app-name.up.railway.app`
   - Copy this URL for frontend configuration

### Option B: Deploy to Render

1. **Sign up/Login**: Go to [Render.com](https://render.com)

2. **Create PostgreSQL Database**:
   - New → PostgreSQL
   - Note connection details

3. **Create Web Service**:
   - New → Web Service
   - Connect GitHub repo
   - Root Directory: `backend`
   - Build Command: `mvn clean package -DskipTests`
   - Start Command: `java -jar target/backend-0.0.1-SNAPSHOT.jar`

4. **Environment Variables**: Same as Railway above

5. **Get Backend URL**: Render provides: `https://your-app-name.onrender.com`

### Option C: Deploy to Fly.io

1. **Install Fly CLI**:
   ```bash
   curl -L https://fly.io/install.sh | sh
   ```

2. **Login**:
   ```bash
   fly auth login
   ```

3. **Create App**:
   ```bash
   cd backend
   fly launch
   ```

4. **Add PostgreSQL**:
   ```bash
   fly postgres create
   fly postgres attach <postgres-app-name>
   ```

5. **Set Environment Variables**:
   ```bash
   fly secrets set DATAGOV_API_KEY=your_api_key_here
   ```

6. **Deploy**:
   ```bash
   fly deploy
   ```

## Step 2: Deploy Frontend to Vercel

### Method 1: Using Vercel CLI (Recommended)

1. **Install Vercel CLI**:
   ```bash
   npm install -g vercel
   ```

2. **Login to Vercel**:
   ```bash
   vercel login
   ```

3. **Configure Backend URL**:
   ```bash
   cd frontend
   ```

   Edit `vercel.json` and update the backend URL:
   ```json
   {
     "rewrites": [
       {
         "source": "/api/(.*)",
         "destination": "https://your-backend-url.railway.app/api/$1"
       }
     ]
   }
   ```

4. **Set Environment Variable**:
   Create `.env.production` in `frontend/` directory:
   ```
   VITE_API_BASE_URL=https://your-backend-url.railway.app
   ```

5. **Deploy**:
   ```bash
   cd frontend
   vercel --prod
   ```

### Method 2: Using Vercel Dashboard

1. **Go to Vercel**: [vercel.com](https://vercel.com)

2. **Import Project**:
   - Click "Add New" → "Project"
   - Import your GitHub repository

3. **Configure Project**:
   - **Framework Preset**: Vite
   - **Root Directory**: `frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`

4. **Environment Variables**:
   - Go to Settings → Environment Variables
   - Add: `VITE_API_BASE_URL` = `https://your-backend-url.railway.app`
   - Apply to: Production, Preview, Development

5. **Configure Rewrites** (in `vercel.json`):
   Update the backend URL in `frontend/vercel.json` before deploying.

6. **Deploy**:
   - Click "Deploy"
   - Vercel will build and deploy your frontend

## Step 3: Update CORS Settings

Make sure your backend allows requests from your Vercel domain.

### Update Backend CORS Configuration

Edit `backend/src/main/java/com/mgnrega/backend/config/WebConfig.java`:

```java
@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://your-app.vercel.app",
    "https://*.vercel.app"  // Allow all Vercel preview deployments
})
```

Or use environment variable:

```java
@CrossOrigin(origins = "${CORS_ALLOWED_ORIGINS:http://localhost:3000}")
```

And set `CORS_ALLOWED_ORIGINS` environment variable in your backend hosting service:
```
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app,https://*.vercel.app
```

## Step 4: Verify Deployment

1. **Check Frontend**: Visit your Vercel URL
2. **Check API**: Visit `https://your-vercel-url.vercel.app/api/health`
3. **Test Full Flow**: 
   - Select a district
   - View performance data
   - Check browser console for errors

## Troubleshooting

### Frontend can't connect to backend

**Issue**: CORS errors or 404 on API calls

**Solutions**:
1. Check backend URL in `frontend/vercel.json`
2. Verify `VITE_API_BASE_URL` environment variable
3. Check backend CORS settings
4. Verify backend is running and accessible

### Backend deployment fails

**Issue**: Build errors or runtime errors

**Solutions**:
1. Check Java version (needs Java 17)
2. Verify environment variables are set
3. Check database connection string
4. Review build logs in hosting service

### Database connection issues

**Issue**: Backend can't connect to PostgreSQL

**Solutions**:
1. Verify database credentials
2. Check if database is running
3. Ensure DATABASE_URL is correctly formatted
4. Check network/firewall settings

## Environment Variables Summary

### Backend (Railway/Render/Fly.io):
```
DATABASE_URL=jdbc:postgresql://host:port/database
DATABASE_USER=postgres
DATABASE_PASSWORD=password
DATAGOV_API_KEY=your_api_key
SERVER_PORT=8080
USE_DATABASE=true
SPRING_PROFILES_ACTIVE=prod
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app
```

### Frontend (Vercel):
```
VITE_API_BASE_URL=https://your-backend-url.railway.app
```

## Quick Deploy Commands

### Deploy Backend to Railway:
```bash
# Push code to GitHub first
git push origin main

# Then deploy via Railway dashboard or CLI
railway up
```

### Deploy Frontend to Vercel:
```bash
cd frontend
vercel --prod
```

## Updating Deployments

### Update Backend:
1. Push changes to GitHub
2. Railway/Render will auto-deploy
3. Check deployment logs

### Update Frontend:
```bash
cd frontend
npm run build
vercel --prod
```

Or push to GitHub if connected to Vercel (auto-deploys).

## Cost Estimates

- **Vercel**: Free tier (hobby) - 100GB bandwidth/month
- **Railway**: $5/month after free trial (500 hours)
- **Render**: Free tier available (with limitations)
- **Fly.io**: Free tier available (shared-cpu-1x)

## Additional Resources

- [Vercel Documentation](https://vercel.com/docs)
- [Railway Documentation](https://docs.railway.app)
- [Render Documentation](https://render.com/docs)
- [Fly.io Documentation](https://fly.io/docs)

