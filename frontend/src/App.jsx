import { useEffect, useMemo, useState } from 'react';
import './App.css';

// API Base URL configuration
// In production (Vercel), use environment variable or relative URL
// In development, use localhost
const getApiBaseUrl = () => {
  // Priority 1: Environment variable (set in Vercel)
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }
  // Priority 2: In production, use relative URL (same domain)
  if (import.meta.env.PROD) {
    return ''; // Relative URLs work with Vercel rewrites
  }
  // Priority 3: Development default
  return 'http://localhost:9090';
};

const API_BASE_URL = getApiBaseUrl();

function App() {
  const [districts, setDistricts] = useState([]);
  const [selected, setSelected] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [perfLoading, setPerfLoading] = useState(false);
  const [perfError, setPerfError] = useState(null);
  const [performance, setPerformance] = useState(null);

  const [comparative, setComparative] = useState(null);
  const [compLoading, setCompLoading] = useState(false);
  const [compError, setCompError] = useState(null);
  const [showCompare, setShowCompare] = useState(false);
  const [compareDistrict, setCompareDistrict] = useState('');

  const [lang, setLang] = useState('en'); // 'en' | 'hi'

  const t = useMemo(() => ({
    en: {
      heading: 'Select Your District',
      view: 'View Performance',
      selected: 'Selected District',
      recent: 'Recent Months',
      speak: 'Play Audio Help',
      speaking: 'Speaking…',
      tableTitle: 'Detailed Records',
      compareState: 'Compare with State Average',
      compareDistrict: 'Compare with Another District',
      aboveAverage: 'Above State Average',
      belowAverage: 'Below State Average',
      difference: 'Difference',
      betterDistrict: 'Better Performing District',
      selectDistrict: 'Select District to Compare'
    },
    hi: {
      heading: 'अपना ज़िला चुनें',
      view: 'प्रदर्शन देखें',
      selected: 'चुना गया ज़िला',
      recent: 'हाल के महीने',
      speak: 'आवाज़ में जानकारी',
      speaking: 'बोला जा रहा है…',
      tableTitle: 'विस्तृत रिकॉर्ड',
      compareState: 'राज्य औसत से तुलना',
      compareDistrict: 'दूसरे ज़िले से तुलना',
      aboveAverage: 'राज्य औसत से ऊपर',
      belowAverage: 'राज्य औसत से नीचे',
      difference: 'अंतर',
      betterDistrict: 'बेहतर प्रदर्शन करने वाला ज़िला',
      selectDistrict: 'तुलना के लिए ज़िला चुनें'
    }
  }), []);

  useEffect(() => {
    console.log('🔍 Initializing app. API Base URL:', API_BASE_URL);
    setLoading(true);
    const districtsUrl = `${API_BASE_URL}/api/districts`;
    console.log('📡 Fetching districts from:', districtsUrl);
    
    fetch(districtsUrl)
      .then((res) => {
        console.log('📥 Districts response status:', res.status, res.statusText);
        if (!res.ok) {
          throw new Error(`Failed to fetch districts: ${res.status} ${res.statusText}`);
        }
        return res.json();
      })
      .then((data) => {
        console.log('✅ Districts data received:', data);
        setDistricts(Array.isArray(data) ? data : []);
        if (Array.isArray(data) && data.length > 0) {
          setSelected(data[0].name);
          console.log('✓ Selected first district:', data[0].name);
        } else {
          console.warn('⚠️ No districts in response');
        }
        setLoading(false);
      })
      .catch((err) => {
        console.error('❌ Error fetching districts:', err);
        setError(`Failed to load districts: ${err.message}. Make sure backend is running on ${API_BASE_URL}`);
        setLoading(false);
      });
  }, []);

  // Bonus: Try geolocating user to preselect district (non-blocking)
  useEffect(() => {
    if (!('geolocation' in navigator)) return;
    const controller = new AbortController();
    navigator.geolocation.getCurrentPosition(async (pos) => {
      try {
        const { latitude, longitude } = pos.coords;
        const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}`;
        const res = await fetch(url, { headers: { 'Accept': 'application/json' }, signal: controller.signal });
        const data = await res.json();
        const districtName = (data && (data.address?.county || data.address?.state_district || data.address?.district)) || '';
        if (!districtName) return;
        const match = districts.find(d => districtName.toLowerCase().includes(d.name.toLowerCase()));
        if (match) setSelected(match.name);
      } catch (_) {}
    });
    return () => controller.abort();
  }, [districts]);

  const fetchPerformance = async () => {
    if (!selected) return;
    setPerfLoading(true);
    setPerfError(null);
    setPerformance(null);
    setComparative(null);
    try {
      const params = new URLSearchParams({ state: 'Maharashtra', district: selected, limit: '12' });
      const res = await fetch(`${API_BASE_URL}/api/performance?${params.toString()}`);
      if (!res.ok) throw new Error('Failed to fetch performance');
      const text = await res.text();
      let json = null;
      try { 
        json = JSON.parse(text); 
        // Debug: log the structure to see what we're getting
        console.log('📊 Performance data received:', json);
        if (json.records && json.records.length > 0) {
          const firstRecord = json.records[0];
          console.log('📋 First record structure:', firstRecord);
          console.log('🔑 First record keys:', Object.keys(firstRecord));
          console.log('💰 Wage data check:');
          console.log('  avg_wage_rate:', firstRecord.avg_wage_rate, typeof firstRecord.avg_wage_rate);
          console.log('  total_wages:', firstRecord.total_wages, typeof firstRecord.total_wages);
          console.log('  households_worked:', firstRecord.households_worked, typeof firstRecord.households_worked);
          console.log('  persondays_generated:', firstRecord.persondays_generated, typeof firstRecord.persondays_generated);
        } else {
          console.warn('⚠️ No records in response:', json);
        }
      } catch (e) {
        console.error('❌ Failed to parse JSON:', e, 'Raw text:', text.substring(0, 200));
      }
      setPerformance(json || { raw: text });
    } catch (e) {
      setPerfError(e.message);
    } finally {
      setPerfLoading(false);
    }
  };

  const fetchStateComparison = async () => {
    if (!selected) return;
    setCompLoading(true);
    setCompError(null);
    try {
      const params = new URLSearchParams({ state: 'Maharashtra', district: selected });
      const res = await fetch(`${API_BASE_URL}/api/comparatives/state-average?${params.toString()}`);
      if (!res.ok) throw new Error('Failed to fetch comparison');
      const data = await res.json();
      setComparative(data);
    } catch (e) {
      setCompError(e.message);
    } finally {
      setCompLoading(false);
    }
  };

  const fetchDistrictComparison = async () => {
    if (!selected || !compareDistrict) return;
    setCompLoading(true);
    setCompError(null);
    try {
      const params = new URLSearchParams({ 
        state: 'Maharashtra', 
        district1: selected, 
        district2: compareDistrict 
      });
      const res = await fetch(`${API_BASE_URL}/api/comparatives/district-comparison?${params.toString()}`);
      if (!res.ok) throw new Error('Failed to fetch comparison');
      const data = await res.json();
      setComparative(data);
    } catch (e) {
      setCompError(e.message);
    } finally {
      setCompLoading(false);
    }
  };

  const icons = {
    fin_year: '📅',
    month: '🗓️',
    state_name: '🗺️',
    district_name: '🏢',
    households_worked: '👨‍👩‍👧‍👦',
    persondays_generated: '⏱️',
    no_of_ongoing_works: '🏗️',
    no_of_completed_works: '✅',
    avg_wage_rate: '₹',
    total_wages: '💰'
  };

  const labelFor = (k) => {
    const pretty = k.replace(/_/g,' ').replace(/\b\w/g, c => c.toUpperCase());
    const mapHi = {
      fin_year: 'वित्तीय वर्ष',
      month: 'महीना',
      state_name: 'राज्य',
      district_name: 'ज़िला',
      households_worked: 'काम करने वाले परिवार',
      persondays_generated: 'सृजित मानव-दिवस',
      no_of_ongoing_works: 'चल रहे कार्य',
      no_of_completed_works: 'पूरा हुए कार्य',
      avg_wage_rate: 'औसत मज़दूरी दर',
      total_wages: 'कुल मज़दूरी'
    };
    if (lang === 'hi' && mapHi[k]) return mapHi[k];
    return pretty;
  };

  // Format numbers in a human-readable way
  const formatNumber = (value) => {
    if (value === null || value === undefined || value === '-') return '-';
    const num = typeof value === 'string' ? parseFloat(value.replace(/,/g, '')) : Number(value);
    if (isNaN(num)) return value;
    
    // Format large numbers with Indian numbering system
    if (num >= 10000000) return `${(num / 10000000).toFixed(2)} Cr`; // Crores
    if (num >= 100000) return `${(num / 100000).toFixed(2)} Lakh`; // Lakhs
    if (num >= 1000) return `${(num / 1000).toFixed(1)}K`;
    return num.toLocaleString('en-IN');
  };

  // Format currency values
  const formatCurrency = (value) => {
    if (value === null || value === undefined || value === '-') return '-';
    const num = typeof value === 'string' ? parseFloat(value.replace(/,/g, '')) : Number(value);
    if (isNaN(num)) return value;
    
    if (num >= 10000000) return `₹${(num / 10000000).toFixed(2)} Cr`;
    if (num >= 100000) return `₹${(num / 100000).toFixed(2)} Lakh`;
    if (num >= 1000) return `₹${(num / 1000).toFixed(1)}K`;
    return `₹${num.toLocaleString('en-IN')}`;
  };

  // Format percentage
  const formatPercent = (value) => {
    if (value === null || value === undefined || value === '-') return '-';
    const num = typeof value === 'string' ? parseFloat(value.replace(/,/g, '')) : Number(value);
    if (isNaN(num)) return value;
    return `${num.toFixed(1)}%`;
  };

  // Get readable description for each field
  const getFieldDescription = (key) => {
    const descriptions = {
      en: {
        households_worked: 'Families who received work',
        persondays_generated: 'Total days of employment created',
        no_of_ongoing_works: 'Projects currently running',
        no_of_completed_works: 'Projects finished this period',
        avg_wage_rate: 'Average payment per day per person',
        total_wages: 'Total money paid to workers'
      },
      hi: {
        households_worked: 'काम पाने वाले परिवार',
        persondays_generated: 'बनाए गए रोज़गार के दिन',
        no_of_ongoing_works: 'अभी चल रहे प्रोजेक्ट',
        no_of_completed_works: 'इस अवधि में पूरे हुए प्रोजेक्ट',
        avg_wage_rate: 'प्रति व्यक्ति प्रति दिन औसत भुगतान',
        total_wages: 'मजदूरों को कुल भुगतान'
      }
    };
    return descriptions[lang]?.[key] || '';
  };

  const renderCards = () => {
    if (!performance) {
      console.log('No performance data available');
      return null;
    }
    if (!performance.records || performance.records.length === 0) {
      console.log('Performance data exists but no records:', performance);
      return (
        <div className="card" style={{ borderColor: 'var(--warning)', marginTop: 16 }}>
          <div style={{ color: 'var(--warning)' }}>
            ⚠️ {lang === 'hi' ? 'कोई डेटा नहीं मिला' : 'No data found'}
          </div>
          <div className="subtle" style={{ marginTop: 8 }}>
            {lang === 'hi' 
              ? 'इस जिले के लिए कोई रिकॉर्ड उपलब्ध नहीं है।'
              : 'No records available for this district.'}
          </div>
          {performance.source && (
            <div className="subtle" style={{ marginTop: 4, fontSize: '0.85em' }}>
              Source: {performance.source}
            </div>
          )}
        </div>
      );
    }
    const rec = performance.records[0];
    console.log('Rendering cards with record:', rec);
    const cards = [];

    const candidateKeys = [
      'fin_year','month','state_name','district_name',
      'households_worked','persondays_generated',
      'no_of_ongoing_works','no_of_completed_works','avg_wage_rate','total_wages'
    ];

    candidateKeys.forEach(k => {
      // Try both snake_case and camelCase
      const value = rec[k] ?? rec[k.split('_').map((w, i) => i === 0 ? w : w[0].toUpperCase() + w.slice(1)).join('')];
      if (value !== undefined && value !== null) {
        cards.push({ k: labelFor(k), v: value, icon: icons[k] || '' });
      }
    });

    // If no cards found, try to use any available keys
    if (cards.length === 0) {
      console.warn('No standard keys found, using all available keys:', Object.keys(rec));
      Object.keys(rec).slice(0, 6).forEach(k => {
        if (rec[k] !== undefined && rec[k] !== null) {
          cards.push({ k: k, v: String(rec[k]), icon: '' });
        }
      });
    }
    
    console.log('Cards to render:', cards);

    return (
      <div className="grid">
        {cards.map((c, idx) => {
          const fieldKey = Object.keys(rec).find(k => labelFor(k) === c.k) || '';
          const description = getFieldDescription(fieldKey);
          
          // Format the value based on field type
          let formattedValue = c.v;
          if (fieldKey === 'avg_wage_rate' || fieldKey === 'total_wages') {
            formattedValue = formatCurrency(c.v);
          } else if (['households_worked', 'persondays_generated', 'no_of_ongoing_works', 'no_of_completed_works'].includes(fieldKey)) {
            formattedValue = formatNumber(c.v);
          } else {
            formattedValue = String(c.v).toLocaleString('en-IN');
          }
          
          return (
            <div key={idx} className="card">
              <div className="label">{c.icon} {c.k}</div>
              <div className="value" style={{ fontSize: '1.3em', fontWeight: 'bold', marginTop: '4px' }}>
                {formattedValue}
              </div>
              {description && (
                <div className="subtle" style={{ marginTop: '6px', fontSize: '0.85em', lineHeight: '1.3' }}>
                  {description}
                </div>
              )}
            </div>
          );
        })}
      </div>
    );
  };

  const renderRecent = () => {
    if (!performance || !performance.records) return null;
    return (
      <div style={{ marginTop: 16 }}>
        <h3 className="sectionTitle">{t[lang].recent}</h3>
        <ul className="subtle">
          {performance.records.map((r, i) => (
            <li key={i}>{[r.month, r.fin_year, r.state_name, r.district_name].filter(Boolean).join(' • ')}</li>
          ))}
        </ul>
      </div>
    );
  };

  const renderTrends = () => {
    if (!performance || !performance.records || performance.records.length === 0) return null;
    const rows = [...performance.records].filter(r => r.persondays_generated).map(r => ({
      label: `${r.month || ''}/${r.fin_year || ''}`,
      value: Number(String(r.persondays_generated).replace(/,/g,'')) || 0
    })).reverse();
    if (rows.length < 2) return null;
    const width = 640, height = 160, pad = 24;
    const xs = rows.map((_, i) => i);
    const ys = rows.map(r => r.value);
    const minY = Math.min(...ys), maxY = Math.max(...ys);
    const x = (i) => pad + (i * (width - pad*2)) / (rows.length - 1);
    const y = (v) => height - pad - ((v - minY) * (height - pad*2)) / Math.max(1, (maxY - minY));
    const path = rows.map((r, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(r.value)}`).join(' ');
    const avg = ys.reduce((a,b)=>a+b,0)/ys.length;
    const stateAvgText = `${Math.round(avg).toLocaleString('en-IN')}`;
    return (
      <div className="card" style={{ marginTop: 12 }}>
        <div className="label">
          📈 {lang === 'hi' ? 'रुझान: रोज़गार दिवस (पिछले ' + rows.length + ' महीने)' : `Trend: Employment Days Created (last ${rows.length} months)`}
        </div>
        <div className="subtle" style={{ marginBottom: 8, fontSize: '0.9em' }}>
          {lang === 'hi' 
            ? 'यह ग्राफ दिखाता है कि पिछले महीनों में कितने रोज़गार दिवस बनाए गए'
            : 'This graph shows how many employment days were created over the past months'}
        </div>
        <svg width={width} height={height} className="trend">
          <rect x="0" y="0" width={width} height={height} fill="transparent" />
          <path d={path} fill="none" stroke="var(--primary)" strokeWidth="2" />
          {rows.map((r,i)=> (
            <circle key={i} cx={x(i)} cy={y(r.value)} r="2.5" fill="var(--primary)" />
          ))}
          {/* average line */}
          <line x1={pad} x2={width-pad} y1={y(avg)} y2={y(avg)} stroke="#2a3244" strokeDasharray="4 4" />
        </svg>
        <div className="subtle" style={{ marginTop: 8 }}>
          {lang === 'hi' ? 'औसत' : 'Average'}: <strong>{stateAvgText}</strong> {lang === 'hi' ? 'दिवस' : 'days'}
        </div>
      </div>
    );
  };

  const renderTable = () => {
    if (!performance || !performance.records || performance.records.length === 0) return null;
    const rows = performance.records;
    
    // Helper to get value from object - supports both snake_case (API) and camelCase (Java entity)
    const val = (o, k) => {
      if (!o || typeof o !== 'object') {
        console.warn('val() called with invalid object:', o);
        return '-';
      }
      
      // Try snake_case first (API format)
      if (o[k] !== undefined && o[k] !== null && o[k] !== '') {
        return o[k];
      }
      
      // Try camelCase (Java entity format) - convert snake_case to camelCase
      const camelKey = k.split('_').map((word, i) => 
        i === 0 ? word : word.charAt(0).toUpperCase() + word.slice(1)
      ).join('');
      if (o[camelKey] !== undefined && o[camelKey] !== null && o[camelKey] !== '') {
        return o[camelKey];
      }
      
      // Try alternative field names
      const alternatives = {
        'avg_wage_rate': ['Average_Wage_rate_per_day_per_person', 'average_wage_rate'],
        'total_wages': ['Material_and_skilled_Wages', 'Wages', 'Total_Wages'],
        'households_worked': ['Total_Households_Worked', 'Households_Worked', 'No_of_Households_Worked', 'Number_of_Households_Worked'],
        'persondays_generated': ['Total_Persondays_Generated', 'Persondays_Generated', 'Persondays_of_Central_Liability_so_far'],
        'no_of_ongoing_works': ['Number_of_Ongoing_Works', 'No_of_Ongoing_Works', 'Ongoing_Works', 'OngoingWorks'],
        'no_of_completed_works': ['Number_of_Completed_Works', 'No_of_Completed_Works', 'Completed_Works', 'CompletedWorks']
      };
      
      if (alternatives[k]) {
        for (const altKey of alternatives[k]) {
          if (o[altKey] !== undefined && o[altKey] !== null && o[altKey] !== '') {
            return o[altKey];
          }
        }
      }
      
      return '-';
    };
    const badge = (num, fieldKey = '') => {
      if (num === '-' || num === undefined || num === null) return <span className="badge">-</span>;
      const n = Number(num.toString().replace(/,/g,''));
      const cls = isNaN(n) ? 'badge' : n > 0 ? 'badge ok' : 'badge warn';
      
      // Format based on field type
      let formatted = num;
      if (fieldKey === 'avg_wage_rate' || fieldKey === 'total_wages') {
        formatted = formatCurrency(num);
      } else if (typeof n === 'number') {
        formatted = n.toLocaleString('en-IN');
      }
      
      return <span className={cls}>{formatted}</span>;
    };

    return (
      <div className="tableWrap">
        <h3 className="sectionTitle">{t[lang].tableTitle}</h3>
        <table className="table">
          <thead>
            <tr>
              <th>{labelFor('month')}</th>
              <th>{labelFor('fin_year')}</th>
              <th>{labelFor('households_worked')}</th>
              <th>{labelFor('persondays_generated')}</th>
              <th>{labelFor('no_of_ongoing_works')}</th>
              <th>{labelFor('no_of_completed_works')}</th>
              <th>{labelFor('avg_wage_rate')}</th>
              <th>{labelFor('total_wages')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i}>
                <td><strong>{val(r,'month')}</strong></td>
                <td>{val(r,'fin_year')}</td>
                <td>{badge(val(r,'households_worked'), 'households_worked')}</td>
                <td>{badge(val(r,'persondays_generated'), 'persondays_generated')}</td>
                <td>{badge(val(r,'no_of_ongoing_works'), 'no_of_ongoing_works')}</td>
                <td>{badge(val(r,'no_of_completed_works'), 'no_of_completed_works')}</td>
                <td>{badge(val(r,'avg_wage_rate'), 'avg_wage_rate')}</td>
                <td>{badge(val(r,'total_wages'), 'total_wages')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  const renderComparatives = () => {
    if (!comparative) return null;

    // Show error if present
    if (comparative.error) {
      return (
        <div className="card" style={{ marginTop: 16, borderColor: 'var(--danger)' }}>
          <div style={{ color: 'var(--danger)' }}>
            <strong>⚠️ {lang === 'hi' ? 'त्रुटि' : 'Error'}:</strong> {comparative.error}
          </div>
          <div className="subtle" style={{ marginTop: 8 }}>
            {lang === 'hi' 
              ? 'पहले जिला प्रदर्शन डेटा लोड करें। "View Performance" बटन पर क्लिक करें।'
              : 'Please load district performance data first. Click "View Performance" button.'}
          </div>
          {comparative.availableStates && comparative.availableStates.length > 0 && (
            <div className="subtle" style={{ marginTop: 8, padding: '8px', background: 'var(--surface)', borderRadius: '4px' }}>
              <strong>{lang === 'hi' ? 'उपलब्ध राज्य:' : 'Available states:'}</strong> {comparative.availableStates.join(', ')}
            </div>
          )}
          {comparative.hint && (
            <div className="subtle" style={{ marginTop: 8, fontSize: '0.9em' }}>
              💡 {comparative.hint}
            </div>
          )}
        </div>
      );
    }

    // State average comparison
    if (comparative.stateAveragePersondays !== undefined) {
      const isAbove = comparative.aboveStateAverage;
      const diffPercent = comparative.persondaysDifferencePercent || 0;
      const stateAvg = comparative.stateAveragePersondays || 0;
      const districtValue = comparative.districtPersondays || 0;
      
      // Don't show comparison if both values are 0 or missing
      if (stateAvg === 0 && districtValue === 0) {
        const availableDistricts = comparative.availableDistricts;
        return (
          <div className="card" style={{ marginTop: 16, borderColor: 'var(--danger)' }}>
            <div style={{ color: 'var(--danger)' }}>
              <strong>⚠️ {lang === 'hi' ? 'चेतावनी' : 'Warning'}:</strong> {
                lang === 'hi' 
                  ? 'राज्य औसत और जिला डेटा दोनों 0 हैं। कोई डेटा उपलब्ध नहीं है।'
                  : 'Both state average and district data are 0. No data available.'}
            </div>
            {comparative.districtDataMissing && (
              <div className="subtle" style={{ marginTop: 8 }}>
                {lang === 'hi' 
                  ? 'यह जिला इस अवधि के लिए डेटा में नहीं मिला।'
                  : 'This district was not found in data for this period.'}
                {availableDistricts && availableDistricts.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <strong>{lang === 'hi' ? 'उपलब्ध जिले' : 'Available districts'}:</strong> {availableDistricts.slice(0, 10).join(', ')}
                    {availableDistricts.length > 10 && '...'}
                  </div>
                )}
              </div>
            )}
            {!comparative.districtDataMissing && (
              <div className="subtle" style={{ marginTop: 8 }}>
                {lang === 'hi' 
                  ? 'कृपया "View Performance" बटन पर क्लिक करके डेटा लोड करें।'
                  : 'Please click "View Performance" button to load data.'}
              </div>
            )}
          </div>
        );
      }
      
      return (
        <div className="card" style={{ marginTop: 16 }}>
          <h3 className="sectionTitle">📊 {t[lang].compareState}</h3>
          <div className="grid" style={{ marginTop: 12 }}>
            <div className="card">
              <div className="label">{lang === 'hi' ? 'राज्य औसत' : 'State Average'}</div>
              <div className="value">{formatNumber(comparative.stateAveragePersondays)}</div>
              <div className="subtle" style={{ marginTop: 4 }}>
                {lang === 'hi' ? 'रोज़गार दिवस' : 'Employment Days'}
              </div>
            </div>
            <div className="card">
              <div className="label">{lang === 'hi' ? 'आपका जिला' : 'Your District'}</div>
              <div className="value">{formatNumber(comparative.districtPersondays)}</div>
              <div className="subtle" style={{ marginTop: 4 }}>
                {lang === 'hi' ? 'रोज़गार दिवस' : 'Employment Days'}
              </div>
            </div>
            <div className="card">
              <div className="label">{t[lang].difference}</div>
              <div className="value" style={{ 
                color: stateAvg > 0 && districtValue > 0 ? (isAbove ? 'var(--success)' : 'var(--danger)') : 'var(--text)',
                fontSize: '1.2em'
              }}>
                {stateAvg > 0 && districtValue > 0 ? (
                  <>
                    {diffPercent > 0 ? '+' : ''}{diffPercent.toFixed(1)}%
                  </>
                ) : (
                  '-'
                )}
              </div>
              {stateAvg > 0 && districtValue > 0 && (
                <div className="subtle" style={{ marginTop: 4 }}>
                  {isAbove ? '✅ ' + t[lang].aboveAverage : '⚠️ ' + t[lang].belowAverage}
                </div>
              )}
            </div>
          </div>
          {comparative.month && comparative.year && (
            <div className="subtle" style={{ marginTop: 12, fontSize: '0.85em' }}>
              {lang === 'hi' ? 'अवधि' : 'Period'}: {comparative.month} {comparative.year}
            </div>
          )}
        </div>
      );
    }

    // District comparison
    if (comparative.district1 && comparative.district2) {
      const d1 = comparative.district1;
      const d2 = comparative.district2;
      
      // Check if districts have errors
      if (d1.error || d2.error) {
        return (
          <div className="card" style={{ marginTop: 16, borderColor: 'var(--danger)' }}>
            <div style={{ color: 'var(--danger)' }}>
              <strong>⚠️ {lang === 'hi' ? 'त्रुटि' : 'Error'}:</strong>
              {d1.error || d2.error || 'No data available for comparison'}
            </div>
            <div className="subtle" style={{ marginTop: 8 }}>
              {lang === 'hi' 
                ? 'दोनों जिलों के लिए डेटा उपलब्ध होना चाहिए।'
                : 'Data must be available for both districts. Try fetching performance data first.'}
            </div>
          </div>
        );
      }
      return (
        <div className="card" style={{ marginTop: 16 }}>
          <h3 className="sectionTitle">🔄 {t[lang].compareDistrict}</h3>
          <div className="grid" style={{ marginTop: 12 }}>
            <div className="card">
              <div className="label">{d1.name}</div>
              <div className="value">{d1.persondaysGenerated?.toLocaleString('en-IN') || '-'}</div>
              <div className="subtle">Persondays</div>
            </div>
            <div className="card">
              <div className="label">{d2.name}</div>
              <div className="value">{d2.persondaysGenerated?.toLocaleString('en-IN') || '-'}</div>
              <div className="subtle">Persondays</div>
            </div>
            {comparative.betterDistrict && (
              <div className="card">
                <div className="label">{t[lang].betterDistrict}</div>
                <div className="value" style={{ color: 'var(--success)' }}>
                  ✅ {comparative.betterDistrict}
                </div>
                {comparative.differencePersondays && (
                  <div className="subtle" style={{ marginTop: 4 }}>
                    {Math.abs(comparative.differencePersondays).toLocaleString('en-IN')} more persondays
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      );
    }

    return null;
  };

  const Glossary = () => (
    <details style={{ marginTop: 12 }}>
      <summary>What do these terms mean?</summary>
      <ul className="subtle" style={{ marginTop: 8, lineHeight: 1.8, fontSize: '0.95em' }}>
        {lang === 'hi' ? (
          <>
            <li><b>📋 सृजित मानव-दिवस (Persondays Generated)</b>: जिले में कितने दिनों का रोज़गार दिया गया। जितना अधिक, उतना बेहतर।</li>
            <li><b>👨‍👩‍👧‍👦 काम करने वाले परिवार (Households Worked)</b>: कितने परिवारों को काम मिला। हर परिवार को साल में 100 दिन काम मिलना चाहिए।</li>
            <li><b>👩 महिला मानव-दिवस % (Women Persondays %)</b>: कुल काम में महिलाओं का हिस्सा। 33% से अधिक बेहतर है।</li>
            <li><b>🏗️ चल रहे/पूरे हुए कार्य (Ongoing/Completed Works)</b>: जिले में चल रहे और पूरे हुए MGNREGA प्रोजेक्ट्स।</li>
            <li><b>₹ औसत मज़दूरी दर (Avg Wage Rate)</b>: हर व्यक्ति को हर दिन कितनी मज़दूरी मिली (₹ में)।</li>
            <li><b>💰 कुल मज़दूरी (Total Wages)</b>: सभी मज़दूरों को कुल कितना पैसा दिया गया।</li>
          </>
        ) : (
          <>
            <li><b>📋 Persondays Generated</b>: Total days of employment provided in the district. Higher is better.</li>
            <li><b>👨‍👩‍👧‍👦 Households Worked</b>: Number of families that received work. Each family should get 100 days of work per year.</li>
            <li><b>👩 Women Persondays %</b>: Percentage of workdays provided to women. Above 33% is good.</li>
            <li><b>🏗️ Ongoing/Completed Works</b>: MGNREGA projects running and finished in the district.</li>
            <li><b>₹ Average Wage Rate</b>: How much each person was paid per day (in ₹).</li>
            <li><b>💰 Total Wages</b>: Total money paid to all workers combined.</li>
          </>
        )}
      </ul>
    </details>
  );

  const [speaking, setSpeaking] = useState(false);
  const speakSummary = () => {
    if (!performance || !performance.records || performance.records.length === 0) return;
    const r = performance.records[0];
    const synth = window.speechSynthesis;
    if (!synth) return;

    const textEn = `District ${r.district_name || selected} in ${r.state_name || 'Maharashtra'}. Month ${r.month || ''} of ${r.fin_year || ''}. ` +
      `${r.households_worked ? r.households_worked + ' households worked. ' : ''}` +
      `${r.persondays_generated ? r.persondays_generated + ' person days generated. ' : ''}`;

    const textHi = `${r.state_name || 'महाराष्ट्र'} के ${r.district_name || selected} ज़िले में, ${r.fin_year || ''} के ${r.month || ''} महीने का प्रदर्शन। ` +
      `${r.households_worked ? r.households_worked + ' परिवारों ने काम किया। ' : ''}` +
      `${r.persondays_generated ? r.persondays_generated + ' मानव-दिवस बने। ' : ''}`;

    const utter = new SpeechSynthesisUtterance(lang === 'hi' ? textHi : textEn);
    utter.lang = lang === 'hi' ? 'hi-IN' : 'en-IN';
    utter.onstart = () => setSpeaking(true);
    utter.onend = () => setSpeaking(false);
    synth.cancel();
    synth.speak(utter);
  };

  return (
    <div className="container">
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', gap:12 }}>
        <h1 className="heading" style={{ marginBottom: 0 }}>{t[lang].heading}</h1>
        <div style={{ display:'flex', gap:8 }}>
          <button className="button" style={{ background: lang==='en' ? 'var(--primary)' : 'var(--surface)' }} onClick={() => setLang('en')}>EN</button>
          <button className="button" style={{ background: lang==='hi' ? 'var(--primary)' : 'var(--surface)' }} onClick={() => setLang('hi')}>हिं</button>
        </div>
      </div>

      {loading && <div className="card"><p>⏳ Loading districts...</p></div>}
      {error && (
        <div className="card" style={{ borderColor: 'var(--danger)' }}>
          <div style={{ color: 'var(--danger)' }}>
            <strong>⚠️ Error:</strong> {error}
          </div>
          <div className="subtle" style={{ marginTop: 8 }}>
            Make sure the backend is running on <code>{API_BASE_URL}</code>
          </div>
          <div className="subtle" style={{ marginTop: 4, fontSize: '0.85em' }}>
            API Base URL: <code>{API_BASE_URL}</code>
          </div>
        </div>
      )}
      {!loading && !error && (
        <>
          <div className="row">
            <select
              className="select"
              aria-label={t[lang].heading}
              value={selected}
              onChange={e => setSelected(e.target.value)}
            >
              {districts.map(d => (
                <option key={d.id} value={d.name}>{d.name}</option>
              ))}
            </select>

            <button className="button" onClick={fetchPerformance} disabled={perfLoading}>
              {perfLoading ? 'Loading…' : t[lang].view}
            </button>
          </div>

          <div style={{ display:'flex', gap:10, alignItems:'center', marginTop: 4 }}>
            <div className="subtle">
              <b style={{ color: 'var(--text)' }}>{t[lang].selected}:</b> {selected || '—'}
            </div>
            <button className="button" onClick={speakSummary} disabled={!performance || speaking}>
              {speaking ? t[lang].speaking : `🔊 ${t[lang].speak}`}
            </button>
          </div>

          {perfError && (
            <div className="card" style={{ borderColor: 'var(--danger)', marginTop: 16 }}>
              <div style={{ color: 'var(--danger)' }}>
                <strong>⚠️ Error:</strong> {perfError}
              </div>
              <div className="subtle" style={{ marginTop: 8 }}>
                {lang === 'hi' 
                  ? 'प्रदर्शन डेटा लोड नहीं हो सका। कृपया बाद में पुनः प्रयास करें।'
                  : 'Could not load performance data. Please try again later.'}
              </div>
            </div>
          )}
          {renderCards()}
          {renderTrends()}
          {renderRecent()}
          {renderTable()}

          {performance && (
            <div style={{ marginTop: 24 }}>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
                <button className="button" onClick={fetchStateComparison} disabled={compLoading}>
                  {compLoading ? 'Loading…' : `📊 ${t[lang].compareState}`}
                </button>
                <button className="button" onClick={() => setShowCompare(!showCompare)}>
                  {showCompare ? '✕' : `🔄 ${t[lang].compareDistrict}`}
                </button>
              </div>

              {showCompare && (
                <div className="card" style={{ marginBottom: 16 }}>
                  <label className="label" style={{ display: 'block', marginBottom: 8 }}>
                    {t[lang].selectDistrict}
                  </label>
                  <div className="row" style={{ marginTop: 0 }}>
                    <select
                      className="select"
                      value={compareDistrict}
                      onChange={e => setCompareDistrict(e.target.value)}
                    >
                      <option value="">-- Select --</option>
                      {districts.filter(d => d.name !== selected).map(d => (
                        <option key={d.id} value={d.name}>{d.name}</option>
                      ))}
                    </select>
                    <button 
                      className="button" 
                      onClick={fetchDistrictComparison} 
                      disabled={!compareDistrict || compLoading}
                    >
                      Compare
                    </button>
                  </div>
                </div>
              )}

              {compError && (
                <div className="card" style={{ marginTop: 16, borderColor: 'var(--danger)' }}>
                  <p style={{ color: 'var(--danger)', margin: 0 }}>
                    <strong>⚠️ Error:</strong> {compError}
                  </p>
                  <p className="subtle" style={{ marginTop: 8, marginBottom: 0 }}>
                    {lang === 'hi' 
                      ? 'सुनिश्चित करें कि आपने पहले "View Performance" पर क्लिक किया है और डेटा लोड हो गया है।'
                      : 'Make sure you clicked "View Performance" first and data is loaded.'}
                  </p>
                </div>
              )}
              {renderComparatives()}
            </div>
          )}

          {performance && (
            <details style={{ marginTop: 16 }}>
              <summary>{lang === 'hi' ? '📄 विस्तृत जानकारी' : '📄 Detailed Information'}</summary>
              <div style={{ marginTop: 12, padding: '16px', background: 'var(--surface)', borderRadius: '8px' }}>
                {performance.records && performance.records.length > 0 ? (
                  <div>
                    <h4 style={{ marginTop: 0, marginBottom: 12 }}>
                      {lang === 'hi' ? `${performance.records.length} महीने की जानकारी` : `Information for ${performance.records.length} months`}
                    </h4>
                    <div style={{ display: 'grid', gap: '12px' }}>
                      {performance.records.map((record, idx) => (
                        <div key={idx} style={{ 
                          padding: '12px', 
                          background: 'var(--background)', 
                          borderRadius: '6px',
                          border: '1px solid var(--border)'
                        }}>
                          <div style={{ fontWeight: 'bold', marginBottom: '8px', color: 'var(--primary)' }}>
                            📅 {record.month || '-'} {record.fin_year || ''} - {record.district_name || ''}, {record.state_name || ''}
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '8px', fontSize: '0.9em' }}>
                            {record.households_worked && (
                              <div>
                                <span className="subtle">{lang === 'hi' ? 'परिवार' : 'Families'}: </span>
                                <strong>{formatNumber(record.households_worked)}</strong>
                              </div>
                            )}
                            {record.persondays_generated && (
                              <div>
                                <span className="subtle">{lang === 'hi' ? 'रोज़गार दिवस' : 'Employment Days'}: </span>
                                <strong>{formatNumber(record.persondays_generated)}</strong>
                              </div>
                            )}
                            {record.avg_wage_rate && (
                              <div>
                                <span className="subtle">{lang === 'hi' ? 'औसत मज़दूरी' : 'Avg Wage'}: </span>
                                <strong>{formatCurrency(record.avg_wage_rate)}</strong>
                              </div>
                            )}
                            {record.total_wages && (
                              <div>
                                <span className="subtle">{lang === 'hi' ? 'कुल मज़दूरी' : 'Total Wages'}: </span>
                                <strong>{formatCurrency(record.total_wages)}</strong>
                              </div>
                            )}
                            {record.no_of_ongoing_works && (
                              <div>
                                <span className="subtle">{lang === 'hi' ? 'चल रहे कार्य' : 'Ongoing Works'}: </span>
                                <strong>{formatNumber(record.no_of_ongoing_works)}</strong>
                              </div>
                            )}
                            {record.no_of_completed_works && (
                              <div>
                                <span className="subtle">{lang === 'hi' ? 'पूरे हुए कार्य' : 'Completed Works'}: </span>
                                <strong>{formatNumber(record.no_of_completed_works)}</strong>
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                    {performance.source && (
                      <div className="subtle" style={{ marginTop: 12, fontSize: '0.85em' }}>
                        {lang === 'hi' ? 'डेटा स्रोत' : 'Data Source'}: {performance.source === 'database' 
                          ? (lang === 'hi' ? 'डेटाबेस (स्थानीय)' : 'Database (Local)')
                          : (lang === 'hi' ? 'API (ऑनलाइन)' : 'API (Online)')}
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="subtle">
                    {lang === 'hi' 
                      ? 'कोई जानकारी उपलब्ध नहीं है। कृपया "View Performance" बटन पर क्लिक करें।'
                      : 'No information available. Please click "View Performance" button.'}
                  </div>
                )}
              </div>
            </details>
          )}
          <Glossary />
        </>
      )}
    </div>
  );
}

export default App;

