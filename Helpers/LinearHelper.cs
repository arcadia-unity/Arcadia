#if NET_4_6
using UnityEngine;
using clojure.lang;

namespace Arcadia {

	public static class LinearHelper{

        public static IPersistentVector toAngleAxis(Quaternion q)
        {
            float ang;
            Vector3 axis;
            q.ToAngleAxis(out ang, out axis);
            return PersistentVector.create(ang, axis);
        }

		public static Matrix4x4 matrix (
			float a, float b, float c, float d,
			float e, float f, float g, float h,
			float i, float j, float k, float l,
			float m, float n, float o, float p){
			var mtrx = new Matrix4x4();
			mtrx[0, 0] = a;
			mtrx[0, 1] = b;
			mtrx[0, 2] = c;
			mtrx[0, 3] = d;
			mtrx[1, 0] = e;
			mtrx[1, 1] = f;
			mtrx[1, 2] = g;
			mtrx[1, 3] = h;
			mtrx[2, 0] = i;
			mtrx[2, 1] = j;
			mtrx[2, 2] = k;
			mtrx[2, 3] = l;
			mtrx[3, 0] = m;
			mtrx[3, 1] = n;
			mtrx[3, 2] = o;
			mtrx[3, 3] = p;
			return mtrx;
		}

		public static Matrix4x4 matrixByRows (
			Vector4 a, Vector4 b, Vector4 c, Vector4 d){
			var mtrx = new Matrix4x4();
			mtrx.SetRow(0, a);
			mtrx.SetRow(1, b);
			mtrx.SetRow(2, c);
			mtrx.SetRow(3, d);
			return mtrx;
		}

		public static Matrix4x4 matrixPutColumn(
			Matrix4x4 m, int colInx, Vector4 col){
			m.SetColumn(colInx, col);
			return m;
		}

		public static Matrix4x4 matrixPutRow(
			Matrix4x4 m, int rowInx, Vector4 row){
			m.SetRow(rowInx, row);
			return m;
		}

		public static Matrix4x4 matrixPut(Matrix4x4 m, int row, int col, float val){
			m[row, col] = val;
			return m;
		}

		public static Vector2 putV2(Vector2 v, int inx, float val){
			v[inx] = val;
			return v;
		}
			
		public static Vector3 putV3(Vector3 v, int inx, float val){
			v[inx] = val;
			return v;
		}

		public static Vector4 putV4(Vector4 v, int inx, float val){
			v[inx] = val;
			return v;
		}

		// ============================================================
		// vector help

		public static Vector2 invertV2 (Vector2 v)
		{
			return new Vector2(1 / v.x, 1 / v.y);
		}

		public static Vector3 invertV3 (Vector3 v)
		{
			return new Vector3(1 / v.x, 1 / v.y, 1 / v.z);
		}

		public static Vector4 invertV4 (Vector4 v)
		{
			return new Vector4(1 / v.x, 1 / v.y, 1 / v.z, 1 / v.w);
		}

		// ============================================================
		// swizzle help

		public static Vector2 swizzV2(Vector2 v, int xInx, int yInx){
			return new Vector2(v[xInx], v[yInx]);
		}

		public static Vector2 swizzV2(Vector3 v, int xInx, int yInx){
			return new Vector2(v[xInx], v[yInx]);
		}

		public static Vector2 swizzV2(Vector4 v, int xInx, int yInx){
			return new Vector2(v[xInx], v[yInx]);
		}

		public static Vector3 swizzV3(Vector2 v, int xInx, int yInx, int zInx){
			return new Vector3(v[xInx], v[yInx], v[zInx]);
		}

		public static Vector3 swizzV3(Vector3 v, int xInx, int yInx, int zInx){
			return new Vector3(v[xInx], v[yInx], v[zInx]);
		}

		public static Vector3 swizzV3(Vector4 v, int xInx, int yInx, int zInx){
			return new Vector3(v[xInx], v[yInx], v[zInx]);
		}

		public static Vector4 swizzV4(Vector2 v, int xInx, int yInx, int zInx, int wInx){
			return new Vector4(v[xInx], v[yInx], v[zInx], v[wInx]);
		}

		public static Vector4 swizzV4(Vector3 v, int xInx, int yInx, int zInx, int wInx){
			return new Vector4(v[xInx], v[yInx], v[zInx], v[wInx]);
		}

		public static Vector4 swizzV4(Vector4 v, int xInx, int yInx, int zInx, int wInx){
			return new Vector4(v[xInx], v[yInx], v[zInx], v[wInx]);
		}
	}
	
}
#endif