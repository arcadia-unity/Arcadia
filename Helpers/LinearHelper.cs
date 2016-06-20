using UnityEngine;

namespace Arcadia {

	public class LinearHelper{

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



	}
	
}